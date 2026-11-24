package com.mohitdeo.saga.orchestrator.saga;

import java.util.Optional;

/**
 * The fulfilment steps, in order.
 * <p>
 * PROCESS_PAYMENT is the <b>pivot</b>: once money is captured, the saga only moves forward.
 * APPROVE_ORDER therefore has no compensation and is retried until it succeeds.
 */
public enum SagaStep {
    CREATE_ORDER(true),
    RESERVE_INVENTORY(true),
    PROCESS_PAYMENT(true),
    APPROVE_ORDER(false);

    private final boolean compensable;

    SagaStep(boolean compensable) {
        this.compensable = compensable;
    }

    public boolean isCompensable() {
        return compensable;
    }

    public Optional<SagaStep> next() {
        int i = ordinal() + 1;
        return i < values().length ? Optional.of(values()[i]) : Optional.empty();
    }

    public Optional<SagaStep> previous() {
        int i = ordinal() - 1;
        return i >= 0 ? Optional.of(values()[i]) : Optional.empty();
    }
}
