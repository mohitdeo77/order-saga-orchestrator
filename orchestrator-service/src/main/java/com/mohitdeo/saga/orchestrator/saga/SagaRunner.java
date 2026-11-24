package com.mohitdeo.saga.orchestrator.saga;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.saga.orchestrator.config.SagaProperties;
import com.mohitdeo.saga.orchestrator.participant.StepOutcome;
import com.mohitdeo.saga.orchestrator.saga.SagaStepLog.Phase;
import com.mohitdeo.saga.orchestrator.saga.SagaStepLog.Result;

/**
 * Drives one saga step by step.
 *
 * <h2>The loop</h2>
 * <ol>
 *   <li>Read the committed state.</li>
 *   <li>Call the participant, <b>outside</b> any database transaction.</li>
 *   <li>Lock the saga row, check we still own the lease, record the outcome and the next
 *       state, then commit.</li>
 * </ol>
 * Each transition is committed before the next call, so a crash at any point leaves the
 * saga in a state the recovery job can resume. Participant calls are idempotent per saga
 * id, so repeating a call after a crash is safe.
 *
 * <h2>Failure handling</h2>
 * <ul>
 *   <li><b>Rejected</b> (business no): compensate the steps before this one.</li>
 *   <li><b>Transient</b> after in-process retries: store a retry with exponential backoff.
 *       Once those run out, compensate starting with <i>this</i> step, because its outcome
 *       is unknown. Compensations are idempotent and handle "never happened".</li>
 *   <li>Past the pivot (payment captured) the saga only moves forward: APPROVE_ORDER is
 *       retried and is never undone.</li>
 * </ul>
 */
@Component
public class SagaRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRunner.class);

    private final SagaRepository sagas;
    private final SagaStepLogRepository stepLog;
    private final SagaStepExecutor executor;
    private final RetryingInvoker invoker;
    private final SagaProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    SagaRunner(SagaRepository sagas, SagaStepLogRepository stepLog, SagaStepExecutor executor,
               RetryingInvoker invoker, SagaProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.sagas = sagas;
        this.stepLog = stepLog;
        this.executor = executor;
        this.invoker = invoker;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Takes the lease on a saga that is due and not owned by a live worker.
     *
     * @return the fencing token if claimed; empty if another worker owns it or nothing is due
     */
    public Optional<UUID> tryClaim(UUID sagaId) {
        return tx.execute(status -> {
            Instant now = clock.instant();
            SagaInstance saga = sagas.findByIdForUpdate(sagaId).orElse(null);
            if (saga == null || saga.getStatus().isTerminal() || saga.isLeasedAt(now)
                    || saga.getNextAttemptAt().isAfter(now)) {
                return Optional.empty();
            }
            UUID token = UUID.randomUUID();
            saga.acquireLease(token, now.plus(props.lease()));
            return Optional.of(token);
        });
    }

    /** Runs a claimed saga until it finishes, waits for a scheduled retry, or loses its lease. */
    public void run(UUID sagaId, UUID leaseToken) {
        try {
            boolean keepGoing = true;
            while (keepGoing) {
                keepGoing = runCurrentStep(sagaId, leaseToken);
            }
        } catch (RuntimeException e) {
            log.error("Saga {} hit an unexpected error; it will be resumed by recovery", sagaId, e);
            releaseAfterCrash(sagaId, leaseToken, e);
        }
    }

    /** @return true if the next step should run straight away */
    private boolean runCurrentStep(UUID sagaId, UUID leaseToken) {
        SagaInstance saga = sagas.findById(sagaId).orElseThrow();
        if (saga.getStatus().isTerminal()) {
            return false;
        }
        SagaStep step = saga.getCurrentStep();
        boolean compensating = saga.getStatus() == SagaStatus.COMPENSATING;
        String operation = (compensating ? "compensate " : "execute ") + step + " for saga " + sagaId;

        StepOutcome outcome = compensating
                ? invoker.invoke(operation, () -> executor.compensate(step, saga))
                : invoker.invoke(operation, () -> executor.execute(step, saga));

        return Boolean.TRUE.equals(tx.execute(status -> {
            SagaInstance fresh = sagas.findByIdForUpdate(sagaId).orElseThrow();
            if (!fresh.isLeaseOwner(leaseToken) || fresh.getCurrentStep() != step
                    || fresh.getStatus().isTerminal()) {
                // Lease expired and another worker took over: drop this result, the new owner decides
                log.warn("Saga {} lost its lease during {}; discarding result", sagaId, step);
                return false;
            }
            Instant now = clock.instant();
            boolean continueNow = compensating
                    ? onCompensationOutcome(fresh, step, outcome, now)
                    : onForwardOutcome(fresh, step, outcome, now);
            if (continueNow) {
                fresh.acquireLease(leaseToken, now.plus(props.lease())); // extend
            }
            return continueNow;
        }));
    }

    private boolean onForwardOutcome(SagaInstance saga, SagaStep step, StepOutcome outcome, Instant now) {
        if (outcome instanceof StepOutcome.Success) {
            record(saga, step, Phase.EXECUTE, Result.SUCCEEDED, null, now);
            Optional<SagaStep> next = step.next();
            if (next.isPresent()) {
                saga.advanceTo(next.get(), now);
                return true;
            }
            saga.complete(now);
            log.info("Saga {} completed", saga.getId());
            return false;
        }

        if (outcome instanceof StepOutcome.Rejected rejected) {
            record(saga, step, Phase.EXECUTE, Result.REJECTED, rejected.reason(), now);
            if (!step.isCompensable()) {
                saga.fail(step + " rejected after the pivot: " + rejected.reason(), now);
                log.error("Saga {} FAILED: {} rejected after payment; manual action required", saga.getId(), step);
                return false;
            }
            // The participant refused and changed nothing: undo only the earlier steps
            saga.beginCompensation(step.previous().orElse(null), step + " rejected: " + rejected.reason(), now);
            return saga.getStatus() == SagaStatus.COMPENSATING;
        }

        StepOutcome.Transient failure = (StepOutcome.Transient) outcome;
        if (scheduleRetryIfAllowed(saga, step, Phase.EXECUTE, failure, now)) {
            return false;
        }
        record(saga, step, Phase.EXECUTE, Result.FAILED, failure.reason(), now);
        String reason = step + " failed after " + props.durable().maxAttempts() + " attempts: " + failure.reason();
        if (!step.isCompensable()) {
            saga.fail(reason, now);
            log.error("Saga {} FAILED past the pivot; manual action required", saga.getId());
            return false;
        }
        // Outcome unknown (did the timed-out call land?), so compensate this step as well
        saga.beginCompensation(step, reason, now);
        return true;
    }

    private boolean onCompensationOutcome(SagaInstance saga, SagaStep step, StepOutcome outcome, Instant now) {
        if (outcome instanceof StepOutcome.Success) {
            record(saga, step, Phase.COMPENSATE, Result.SUCCEEDED, null, now);
            Optional<SagaStep> previous = step.previous();
            if (previous.isPresent()) {
                saga.compensateNext(previous.get(), now);
                return true;
            }
            saga.markCompensated(now);
            log.info("Saga {} compensated: {}", saga.getId(), saga.getFailureReason());
            return false;
        }

        if (outcome instanceof StepOutcome.Rejected rejected) {
            record(saga, step, Phase.COMPENSATE, Result.REJECTED, rejected.reason(), now);
            saga.fail("Compensation of " + step + " rejected: " + rejected.reason(), now);
            log.error("Saga {} FAILED during compensation; manual action required", saga.getId());
            return false;
        }

        StepOutcome.Transient failure = (StepOutcome.Transient) outcome;
        if (scheduleRetryIfAllowed(saga, step, Phase.COMPENSATE, failure, now)) {
            return false;
        }
        record(saga, step, Phase.COMPENSATE, Result.FAILED, failure.reason(), now);
        saga.fail("Compensation of " + step + " failed after retries: " + failure.reason(), now);
        log.error("Saga {} FAILED during compensation; manual action required", saga.getId());
        return false;
    }

    private boolean scheduleRetryIfAllowed(SagaInstance saga, SagaStep step, Phase phase,
                                           StepOutcome.Transient failure, Instant now) {
        int nextAttempt = saga.getAttempt() + 1;
        if (nextAttempt >= props.durable().maxAttempts()) {
            return false;
        }
        Instant retryAt = now.plus(props.durable().delayForAttempt(nextAttempt));
        record(saga, step, phase, Result.RETRY_SCHEDULED,
                "attempt " + nextAttempt + " failed, retry at " + retryAt + ": " + failure.reason(), now);
        saga.scheduleRetry(retryAt, now);
        log.warn("Saga {} {} {} failed transiently; retry scheduled at {}", saga.getId(), phase, step, retryAt);
        return true;
    }

    private void releaseAfterCrash(UUID sagaId, UUID leaseToken, RuntimeException error) {
        try {
            tx.executeWithoutResult(status -> sagas.findByIdForUpdate(sagaId)
                    .filter(s -> s.isLeaseOwner(leaseToken) && !s.getStatus().isTerminal())
                    .ifPresent(s -> {
                        Instant now = clock.instant();
                        s.scheduleRetry(now.plus(props.durable().delayForAttempt(s.getAttempt() + 1)), now);
                    }));
        } catch (RuntimeException e) {
            log.error("Could not release saga {} after error; lease will expire on its own", sagaId, e);
        }
    }

    private void record(SagaInstance saga, SagaStep step, Phase phase, Result result, String detail, Instant now) {
        stepLog.save(new SagaStepLog(saga.getId(), step, phase, result, detail, now));
    }
}
