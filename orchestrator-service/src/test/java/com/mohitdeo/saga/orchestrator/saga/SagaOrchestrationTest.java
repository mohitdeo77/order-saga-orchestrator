package com.mohitdeo.saga.orchestrator.saga;

import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.APPROVE_ORDER;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.CHARGE;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.CREATE_ORDER;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.REFUND;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.REJECT_ORDER;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.RELEASE_STOCK;
import static com.mohitdeo.saga.orchestrator.support.FakeParticipants.RESERVE_STOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.mohitdeo.saga.orchestrator.participant.StepOutcome;
import com.mohitdeo.saga.orchestrator.saga.SagaExceptions.IdempotencyKeyReusedException;
import com.mohitdeo.saga.orchestrator.saga.SagaStepLog.Phase;
import com.mohitdeo.saga.orchestrator.saga.SagaStepLog.Result;
import com.mohitdeo.saga.orchestrator.support.FakeParticipants;
import com.mohitdeo.saga.orchestrator.support.MutableClock;
import com.mohitdeo.saga.orchestrator.support.TestInfrastructure;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class SagaOrchestrationTest {

    private static final List<SagaItem> ITEMS = List.of(
            new SagaItem("LAPTOP-15", 1, new BigDecimal("1200.00")),
            new SagaItem("MOUSE-WL", 2, new BigDecimal("25.00")));

    @Autowired
    SagaService sagaService;
    @Autowired
    SagaRecoveryJob recoveryJob;
    @Autowired
    SagaRunner runner;
    @Autowired
    SagaRepository sagas;
    @Autowired
    FakeParticipants participants;
    @Autowired
    MutableClock clock;

    @BeforeEach
    void reset() {
        participants.reset();
        // Push earlier tests' scheduled retries far into the past so they cannot interfere
        clock.advance(Duration.ofDays(1));
        recoveryJob.recoverDueSagas();
        participants.reset();
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void happyPathRunsAllStepsInOrderAndCompletes() {
        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.totalAmount()).isEqualByComparingTo("1250.00");
        assertThat(participants.calls()).containsExactly(CREATE_ORDER, RESERVE_STOCK, CHARGE, APPROVE_ORDER);
        assertThat(saga.history()).extracting(SagaView.HistoryEntry::result).containsOnly(Result.SUCCEEDED);
    }

    // ------------------------------------------------------------------ business failures → compensation

    @Test
    void paymentDeclinedCompensatesEarlierStepsInReverseOrder() {
        participants.script(CHARGE, StepOutcome.rejected("422 Amount exceeds limit"));

        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.failureReason()).contains("PROCESS_PAYMENT rejected").contains("Amount exceeds limit");
        // The declined charge did nothing, so there is no refund: only earlier steps are undone
        assertThat(participants.calls())
                .containsExactly(CREATE_ORDER, RESERVE_STOCK, CHARGE, RELEASE_STOCK, REJECT_ORDER);
    }

    @Test
    void outOfStockOnlyRejectsTheOrder() {
        participants.script(RESERVE_STOCK, StepOutcome.rejected("422 Not enough stock"));

        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(participants.calls()).containsExactly(CREATE_ORDER, RESERVE_STOCK, REJECT_ORDER);
    }

    @Test
    void firstStepRejectedNeedsNoCompensation() {
        participants.script(CREATE_ORDER, StepOutcome.rejected("400 invalid order"));

        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(participants.calls()).containsExactly(CREATE_ORDER);
    }

    // ------------------------------------------------------------------ transient failures → retries

    @Test
    void shortGlitchIsAbsorbedByInProcessRetry() {
        participants.script(RESERVE_STOCK, StepOutcome.transientFailure("503"));

        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(participants.count(RESERVE_STOCK)).isEqualTo(2);
    }

    @Test
    void longerOutageIsRetriedDurablyWithBackoffThenRecovers() {
        // Both in-process attempts fail on the first run
        participants.script(CHARGE, StepOutcome.transientFailure("503"), StepOutcome.transientFailure("503"));

        SagaView saga = start();
        assertThat(saga.status()).isEqualTo(SagaStatus.STARTED);
        assertThat(saga.currentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
        assertThat(lastHistory(saga).result()).isEqualTo(Result.RETRY_SCHEDULED);

        // Not due yet: recovery must leave it alone
        assertThat(recoveryJob.recoverDueSagas()).isZero();

        clock.advance(Duration.ofSeconds(11));
        recoveryJob.recoverDueSagas();

        assertThat(sagaService.get(saga.sagaId()).status()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void persistentTimeoutCompensatesIncludingTheTimedOutStep() {
        participants.always(RESERVE_STOCK, StepOutcome.transientFailure("I/O error: request timed out"));

        UUID sagaId = start().sagaId();
        clock.advance(Duration.ofSeconds(11));
        recoveryJob.recoverDueSagas();   // durable attempt 2
        clock.advance(Duration.ofSeconds(21));
        recoveryJob.recoverDueSagas();   // durable attempt 3 → exhausted → compensate

        SagaView saga = sagaService.get(sagaId);
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.failureReason()).contains("RESERVE_INVENTORY failed after 3 attempts");
        // The reservation may have landed despite the timeout, so it is released too
        assertThat(participants.calls()).endsWith(RELEASE_STOCK, REJECT_ORDER);
        assertThat(participants.count(RESERVE_STOCK)).isEqualTo(6); // 3 durable x 2 in-process
    }

    @Test
    void failureAfterPivotRecoversForwardAndNeverRefunds() {
        participants.script(APPROVE_ORDER, StepOutcome.transientFailure("503"), StepOutcome.transientFailure("503"));

        UUID sagaId = start().sagaId();
        clock.advance(Duration.ofSeconds(11));
        recoveryJob.recoverDueSagas();

        assertThat(sagaService.get(sagaId).status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(participants.count(REFUND)).isZero();
    }

    @Test
    void rejectedCompensationMarksSagaFailedForManualAction() {
        participants.script(CHARGE, StepOutcome.rejected("422 declined"));
        participants.script(RELEASE_STOCK, StepOutcome.rejected("409 unexpected state"));

        SagaView saga = start();

        assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.failureReason()).contains("Compensation of RESERVE_INVENTORY rejected");
        assertThat(lastHistory(saga).phase()).isEqualTo(Phase.COMPENSATE);
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void sameIdempotencyKeyReturnsTheSameSagaWithoutRunningItAgain() {
        String key = UUID.randomUUID().toString();
        SagaService.StartResult first = sagaService.start("cust-1", ITEMS, key);
        SagaService.StartResult replay = sagaService.start("cust-1", List.of(ITEMS.get(1), ITEMS.get(0)), key);

        assertThat(replay.created()).isFalse();
        assertThat(replay.saga().sagaId()).isEqualTo(first.saga().sagaId());
        assertThat(participants.count(CREATE_ORDER)).isEqualTo(1);
    }

    @Test
    void idempotencyKeyReusedWithDifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();
        sagaService.start("cust-1", ITEMS, key);

        assertThatThrownBy(() -> sagaService.start("cust-2", ITEMS, key))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    // ------------------------------------------------------------------ crash recovery & leases

    @Test
    void sagaAbandonedByCrashedInstanceIsResumedFromItsLastCommittedStep() {
        // Simulate an instance that committed CREATE_ORDER, moved to RESERVE_INVENTORY, then died
        UUID sagaId = UUID.randomUUID();
        SagaInstance orphan = SagaInstance.start(sagaId, "crash-" + sagaId, "hash", "cust-9", ITEMS, clock.instant());
        orphan.advanceTo(SagaStep.RESERVE_INVENTORY, clock.instant());
        orphan.acquireLease(UUID.randomUUID(), clock.instant().plusSeconds(60));
        sagas.saveAndFlush(orphan);

        // Lease is still live: nobody may take over yet
        assertThat(runner.tryClaim(sagaId)).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        recoveryJob.recoverDueSagas();

        assertThat(sagaService.get(sagaId).status()).isEqualTo(SagaStatus.COMPLETED);
        // Resumed where it stopped: CREATE_ORDER is not repeated
        assertThat(participants.calls()).containsExactly(RESERVE_STOCK, CHARGE, APPROVE_ORDER);
    }

    @Test
    void workerThatLostItsLeaseCannotOverwriteProgress() {
        UUID sagaId = UUID.randomUUID();
        SagaInstance saga = SagaInstance.start(sagaId, "lease-" + sagaId, "hash", "cust-3", ITEMS, clock.instant());
        sagas.saveAndFlush(saga);

        UUID staleToken = runner.tryClaim(sagaId).orElseThrow();
        clock.advance(Duration.ofSeconds(61));                 // stale worker stalls past its lease
        UUID freshToken = runner.tryClaim(sagaId).orElseThrow(); // another instance takes over

        runner.run(sagaId, staleToken);
        assertThat(participants.calls()).containsExactly(CREATE_ORDER); // its result was discarded
        assertThat(sagaService.get(sagaId).status()).isEqualTo(SagaStatus.STARTED);

        runner.run(sagaId, freshToken);
        assertThat(sagaService.get(sagaId).status()).isEqualTo(SagaStatus.COMPLETED);
    }

    // ------------------------------------------------------------------ helpers

    private SagaView start() {
        return sagaService.start("cust-1", ITEMS, UUID.randomUUID().toString()).saga();
    }

    private static SagaView.HistoryEntry lastHistory(SagaView saga) {
        return saga.history().get(saga.history().size() - 1);
    }
}
