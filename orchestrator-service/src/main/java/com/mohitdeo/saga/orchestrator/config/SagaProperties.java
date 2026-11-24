package com.mohitdeo.saga.orchestrator.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param async        run sagas on a worker pool (true) or inside the request thread (false, used by tests)
 * @param lease        how long a worker owns a saga before another instance may take it over
 * @param retry        quick in-process retries for one participant call (handles short glitches)
 * @param durable      retries stored in the database across runs and restarts (handles longer outages)
 * @param recovery     the background job that resumes due or abandoned sagas
 * @param participants participant base URLs and HTTP timeouts
 */
@ConfigurationProperties(prefix = "saga")
public record SagaProperties(
        boolean async,
        Duration lease,
        Backoff retry,
        Backoff durable,
        Recovery recovery,
        Participants participants) {

    public record Backoff(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

        /** Exponential backoff: initial * 2^(attempt-1), capped at maxBackoff. */
        public Duration delayForAttempt(int attempt) {
            long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
            Duration delay = initialBackoff.multipliedBy(multiplier);
            return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
        }
    }

    public record Recovery(long intervalMs, int batchSize) {
    }

    public record Participants(String orderUrl, String inventoryUrl, String paymentUrl,
                               Duration connectTimeout, Duration readTimeout) {
    }
}
