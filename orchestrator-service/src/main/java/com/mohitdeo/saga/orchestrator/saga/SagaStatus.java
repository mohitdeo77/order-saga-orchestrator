package com.mohitdeo.saga.orchestrator.saga;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 *  STARTED ──────────────► COMPLETED
 *     │  └──────────────► FAILED        (forward step past the point of no return keeps failing)
 *     ▼
 *  COMPENSATING ─────────► COMPENSATED
 *           └────────────► FAILED       (compensation keeps failing: needs a person to fix)
 * </pre>
 */
public enum SagaStatus {
    STARTED,
    COMPENSATING,
    COMPLETED,
    COMPENSATED,
    FAILED;

    private static final Map<SagaStatus, Set<SagaStatus>> ALLOWED = Map.of(
            STARTED, EnumSet.of(STARTED, COMPENSATING, COMPLETED, COMPENSATED, FAILED),
            COMPENSATING, EnumSet.of(COMPENSATING, COMPENSATED, FAILED),
            COMPLETED, EnumSet.noneOf(SagaStatus.class),
            COMPENSATED, EnumSet.noneOf(SagaStatus.class),
            FAILED, EnumSet.noneOf(SagaStatus.class));

    public boolean canTransitionTo(SagaStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
