package com.mohitdeo.saga.orchestrator.saga;

import java.util.UUID;

public final class SagaExceptions {

    private SagaExceptions() {
    }

    public static class SagaNotFoundException extends RuntimeException {
        public SagaNotFoundException(UUID sagaId) {
            super("Order saga " + sagaId + " does not exist");
        }
    }

    public static class IdempotencyKeyReusedException extends RuntimeException {
        public IdempotencyKeyReusedException(String key) {
            super("Idempotency-Key '" + key + "' was already used with a different request body");
        }
    }
}
