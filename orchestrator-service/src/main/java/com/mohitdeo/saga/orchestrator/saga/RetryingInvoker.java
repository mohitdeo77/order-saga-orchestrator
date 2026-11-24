package com.mohitdeo.saga.orchestrator.saga;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mohitdeo.saga.orchestrator.config.SagaProperties;
import com.mohitdeo.saga.orchestrator.participant.StepOutcome;

/**
 * Quick in-process retries for one participant call. Only transient failures are retried.
 * A business rejection is returned at once. Full jitter keeps many sagas from retrying
 * against a recovering service at the same moment.
 */
@Component
class RetryingInvoker {

    private static final Logger log = LoggerFactory.getLogger(RetryingInvoker.class);

    private final SagaProperties.Backoff policy;

    RetryingInvoker(SagaProperties props) {
        this.policy = props.retry();
    }

    StepOutcome invoke(String operation, Supplier<StepOutcome> call) {
        StepOutcome outcome = call.get();
        for (int attempt = 1; attempt < policy.maxAttempts() && outcome instanceof StepOutcome.Transient t; attempt++) {
            Duration delay = withJitter(policy.delayForAttempt(attempt));
            log.debug("{} failed transiently ({}), retry {} in {} ms", operation, t.reason(), attempt, delay.toMillis());
            if (!sleep(delay)) {
                return outcome;
            }
            outcome = call.get();
        }
        return outcome;
    }

    private static Duration withJitter(Duration max) {
        long millis = max.toMillis();
        return Duration.ofMillis(millis <= 0 ? 0 : ThreadLocalRandom.current().nextLong(millis / 2, millis + 1));
    }

    private static boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
