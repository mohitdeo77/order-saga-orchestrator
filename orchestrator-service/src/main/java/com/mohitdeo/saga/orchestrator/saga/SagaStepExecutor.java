package com.mohitdeo.saga.orchestrator.saga;

import org.springframework.stereotype.Component;

import com.mohitdeo.saga.orchestrator.participant.InventoryClient;
import com.mohitdeo.saga.orchestrator.participant.OrderClient;
import com.mohitdeo.saga.orchestrator.participant.PaymentClient;
import com.mohitdeo.saga.orchestrator.participant.StepOutcome;

/** Maps each step to the participant call that performs it and the call that undoes it. */
@Component
class SagaStepExecutor {

    private final OrderClient orders;
    private final InventoryClient inventory;
    private final PaymentClient payments;

    SagaStepExecutor(OrderClient orders, InventoryClient inventory, PaymentClient payments) {
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
    }

    StepOutcome execute(SagaStep step, SagaInstance saga) {
        return switch (step) {
            case CREATE_ORDER -> orders.createOrder(saga.getId(), saga.getCustomerId(), saga.getItems());
            case RESERVE_INVENTORY -> inventory.reserveStock(saga.getId(), saga.getItems());
            case PROCESS_PAYMENT -> payments.charge(saga.getId(), saga.getCustomerId(), saga.getTotalAmount());
            case APPROVE_ORDER -> orders.approveOrder(saga.getId());
        };
    }

    StepOutcome compensate(SagaStep step, SagaInstance saga) {
        return switch (step) {
            case CREATE_ORDER -> orders.rejectOrder(saga.getId(), saga.getFailureReason());
            case RESERVE_INVENTORY -> inventory.releaseStock(saga.getId());
            case PROCESS_PAYMENT -> payments.refund(saga.getId());
            case APPROVE_ORDER -> throw new IllegalStateException("APPROVE_ORDER is not compensable");
        };
    }
}
