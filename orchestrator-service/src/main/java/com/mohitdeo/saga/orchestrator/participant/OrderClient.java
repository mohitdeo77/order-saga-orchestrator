package com.mohitdeo.saga.orchestrator.participant;

import java.util.List;
import java.util.UUID;

import com.mohitdeo.saga.orchestrator.saga.SagaItem;

public interface OrderClient {

    StepOutcome createOrder(UUID sagaId, String customerId, List<SagaItem> items);

    StepOutcome approveOrder(UUID sagaId);

    StepOutcome rejectOrder(UUID sagaId, String reason);
}
