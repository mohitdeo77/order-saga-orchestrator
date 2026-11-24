package com.mohitdeo.saga.orchestrator.participant;

/**
 * Result of one participant call. The orchestrator reacts differently to each kind:
 * <ul>
 *   <li>{@link Success}: move to the next step.</li>
 *   <li>{@link Rejected}: a business "no" (out of stock, card declined). The participant did
 *       nothing, so retrying is pointless; start compensating.</li>
 *   <li>{@link Transient}: timeout, 5xx or connection error. The participant may or may not
 *       have acted, so retry first; if it still fails, compensate this step too.</li>
 * </ul>
 */
public sealed interface StepOutcome {

    record Success() implements StepOutcome {
    }

    record Rejected(String reason) implements StepOutcome {
    }

    record Transient(String reason) implements StepOutcome {
    }

    static StepOutcome success() {
        return new Success();
    }

    static StepOutcome rejected(String reason) {
        return new Rejected(reason);
    }

    static StepOutcome transientFailure(String reason) {
        return new Transient(reason);
    }
}
