package com.mohitdeo.saga.orchestrator.saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model returned by the API: current state plus the full step history. */
public record SagaView(
        UUID sagaId,
        SagaStatus status,
        SagaStep currentStep,
        String customerId,
        BigDecimal totalAmount,
        String failureReason,
        Instant nextAttemptAt,
        List<Item> items,
        List<HistoryEntry> history) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    public record HistoryEntry(SagaStep step, SagaStepLog.Phase phase, SagaStepLog.Result result,
                               String detail, Instant at) {
    }

    static SagaView of(SagaInstance s, List<SagaStepLog> log) {
        boolean inFlight = !s.getStatus().isTerminal();
        return new SagaView(s.getId(), s.getStatus(), s.getCurrentStep(), s.getCustomerId(), s.getTotalAmount(),
                s.getFailureReason(), inFlight ? s.getNextAttemptAt() : null,
                s.getItems().stream().map(i -> new Item(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList(),
                log.stream().map(e -> new HistoryEntry(e.getStep(), e.getPhase(), e.getResult(), e.getDetail(),
                        e.getCreatedAt())).toList());
    }
}
