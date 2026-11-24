package com.mohitdeo.saga.orchestrator.participant;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentClient {

    StepOutcome charge(UUID sagaId, String customerId, BigDecimal amount);

    StepOutcome refund(UUID sagaId);
}
