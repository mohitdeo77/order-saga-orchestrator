package com.mohitdeo.saga.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    static Payment charged(UUID sagaId, String customerId, BigDecimal amount) {
        return create(sagaId, customerId, amount, PaymentStatus.CHARGED, null);
    }

    static Payment declined(UUID sagaId, String customerId, BigDecimal amount, String reason) {
        return create(sagaId, customerId, amount, PaymentStatus.DECLINED, reason);
    }

    static Payment voidedTombstone(UUID sagaId) {
        return create(sagaId, null, BigDecimal.ZERO, PaymentStatus.VOIDED, null);
    }

    private static Payment create(UUID sagaId, String customerId, BigDecimal amount,
                                  PaymentStatus status, String declineReason) {
        Payment p = new Payment();
        p.sagaId = sagaId;
        p.customerId = customerId;
        p.amount = amount;
        p.status = status;
        p.declineReason = declineReason;
        p.createdAt = p.updatedAt = Instant.now();
        return p;
    }

    void refund() {
        status = PaymentStatus.REFUNDED;
        updatedAt = Instant.now();
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}
