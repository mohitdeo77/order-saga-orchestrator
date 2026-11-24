package com.mohitdeo.saga.orchestrator.saga;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mohitdeo.saga.orchestrator.config.SagaProperties;

/**
 * Resumes sagas that are due: scheduled retries whose backoff has passed, and sagas whose
 * worker died (expired lease). Claiming goes through a row lock plus a lease token, so
 * running this job on several orchestrator instances never runs one saga twice at once.
 */
@Component
public class SagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryJob.class);

    private final SagaRepository sagas;
    private final SagaRunner runner;
    private final SagaDispatcher dispatcher;
    private final SagaProperties props;
    private final Clock clock;

    SagaRecoveryJob(SagaRepository sagas, SagaRunner runner, SagaDispatcher dispatcher,
                    SagaProperties props, Clock clock) {
        this.sagas = sagas;
        this.runner = runner;
        this.dispatcher = dispatcher;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${saga.recovery.interval-ms}")
    void scheduled() {
        recoverDueSagas();
    }

    /** @return number of sagas claimed and dispatched */
    public int recoverDueSagas() {
        List<UUID> due = sagas.findDueForRecovery(clock.instant(), PageRequest.of(0, props.recovery().batchSize()));
        int dispatched = 0;
        for (UUID sagaId : due) {
            var token = runner.tryClaim(sagaId);
            if (token.isPresent()) {
                dispatcher.dispatch(sagaId, token.get());
                dispatched++;
            }
        }
        if (dispatched > 0) {
            log.info("Recovery resumed {} saga(s)", dispatched);
        }
        return dispatched;
    }
}
