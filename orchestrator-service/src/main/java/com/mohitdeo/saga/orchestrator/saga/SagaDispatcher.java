package com.mohitdeo.saga.orchestrator.saga;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import com.mohitdeo.saga.orchestrator.config.SagaProperties;

/** Hands a claimed saga to a worker thread, or runs it inline when async is turned off. */
@Component
class SagaDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SagaDispatcher.class);

    private final SagaRunner runner;
    private final TaskExecutor executor;
    private final boolean async;

    SagaDispatcher(SagaRunner runner, @Qualifier("sagaExecutor") TaskExecutor executor, SagaProperties props) {
        this.runner = runner;
        this.executor = executor;
        this.async = props.async();
    }

    void dispatch(UUID sagaId, UUID leaseToken) {
        if (!async) {
            runner.run(sagaId, leaseToken);
            return;
        }
        try {
            executor.execute(() -> runner.run(sagaId, leaseToken));
        } catch (TaskRejectedException e) {
            // Back-pressure: the saga is safely stored; recovery picks it up once the lease expires
            log.warn("Worker pool full; saga {} deferred to recovery", sagaId);
        }
    }
}
