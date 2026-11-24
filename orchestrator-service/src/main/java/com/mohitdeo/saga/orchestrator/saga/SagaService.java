package com.mohitdeo.saga.orchestrator.saga;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.saga.orchestrator.config.SagaProperties;
import com.mohitdeo.saga.orchestrator.saga.SagaExceptions.IdempotencyKeyReusedException;
import com.mohitdeo.saga.orchestrator.saga.SagaExceptions.SagaNotFoundException;

/**
 * Entry point for placing orders.
 * <p>
 * The saga is stored (with its lease already held by this instance) <b>before</b> any
 * participant is called. So once the client gets a response, the order is durable: if
 * this process dies straight away, the recovery job finishes it.
 */
@Service
public class SagaService {

    private static final Logger log = LoggerFactory.getLogger(SagaService.class);

    public record StartResult(SagaView saga, boolean created) {
    }

    private final SagaRepository sagas;
    private final SagaStepLogRepository stepLog;
    private final SagaDispatcher dispatcher;
    private final SagaProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    SagaService(SagaRepository sagas, SagaStepLogRepository stepLog, SagaDispatcher dispatcher,
                SagaProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.sagas = sagas;
        this.stepLog = stepLog;
        this.dispatcher = dispatcher;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    public StartResult start(String customerId, List<SagaItem> items, String idempotencyKey) {
        String requestHash = RequestFingerprint.of(customerId, items);

        Optional<StartResult> replay = findReplay(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        UUID sagaId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        try {
            tx.executeWithoutResult(status -> {
                Instant now = clock.instant();
                SagaInstance saga = SagaInstance.start(sagaId, idempotencyKey, requestHash, customerId, items, now);
                saga.acquireLease(leaseToken, now.plus(props.lease()));
                sagas.saveAndFlush(saga);
            });
        } catch (DataIntegrityViolationException e) {
            // Concurrent request with the same key won the insert
            return findReplay(idempotencyKey, requestHash).orElseThrow(() -> e);
        }

        log.info("Saga {} started for customer {}", sagaId, customerId);
        dispatcher.dispatch(sagaId, leaseToken);
        return new StartResult(get(sagaId), true);
    }

    public SagaView get(UUID sagaId) {
        SagaInstance saga = sagas.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));
        return SagaView.of(saga, stepLog.findBySagaIdOrderByIdAsc(sagaId));
    }

    private Optional<StartResult> findReplay(String idempotencyKey, String requestHash) {
        return sagas.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            return new StartResult(get(existing.getId()), false);
        });
    }
}
