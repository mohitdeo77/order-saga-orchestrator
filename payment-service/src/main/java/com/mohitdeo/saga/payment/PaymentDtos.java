package com.mohitdeo.saga.payment;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record ChargeRequest(@NotNull UUID sagaId, @NotBlank String customerId,
                                @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record PaymentView(UUID sagaId, String customerId, BigDecimal amount, PaymentStatus status,
                              String declineReason) {
        static PaymentView from(Payment p) {
            return new PaymentView(p.getSagaId(), p.getCustomerId(), p.getAmount(), p.getStatus(),
                    p.getDeclineReason());
        }
    }

    public record ChargeResult(PaymentView payment, boolean created) {
    }
}
