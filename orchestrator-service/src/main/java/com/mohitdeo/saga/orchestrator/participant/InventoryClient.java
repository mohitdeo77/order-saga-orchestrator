package com.mohitdeo.saga.orchestrator.participant;

import java.util.List;
import java.util.UUID;

import com.mohitdeo.saga.orchestrator.saga.SagaItem;

public interface InventoryClient {

    StepOutcome reserveStock(UUID sagaId, List<SagaItem> items);

    StepOutcome releaseStock(UUID sagaId);
}
