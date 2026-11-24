package com.mohitdeo.saga.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    /** One order per saga. The saga id is also the idempotency key for every call to this service. */
    @Column(name = "saga_id", nullable = false, unique = true)
    private UUID sagaId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
    }

    static Order pending(UUID sagaId, String customerId, List<OrderLine> lines) {
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.sagaId = sagaId;
        o.customerId = customerId;
        o.lines = new ArrayList<>(lines);
        o.totalAmount = lines.stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        o.status = OrderStatus.PENDING;
        o.createdAt = o.updatedAt = Instant.now();
        return o;
    }

    /**
     * A reject that arrives before the order exists, for example when the create call timed out
     * and the saga compensated. The tombstone makes the delayed create fail instead of leaving
     * an orphaned PENDING order behind.
     */
    static Order rejectedTombstone(UUID sagaId, String reason) {
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.sagaId = sagaId;
        o.status = OrderStatus.REJECTED;
        o.rejectionReason = reason;
        o.createdAt = o.updatedAt = Instant.now();
        return o;
    }

    void approve() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order for saga " + sagaId + " is " + status);
        }
        status = OrderStatus.APPROVED;
        updatedAt = Instant.now();
    }

    void reject(String reason) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order for saga " + sagaId + " is " + status);
        }
        status = OrderStatus.REJECTED;
        rejectionReason = reason;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }
}
