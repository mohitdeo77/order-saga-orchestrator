package com.mohitdeo.saga.orchestrator.saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

/**
 * The saga's durable state. Every transition is committed before the next participant call,
 * so after a crash the saga resumes from its last committed step.
 */
@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saga_items", joinColumns = @JoinColumn(name = "saga_id"))
    private List<SagaItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private SagaStep currentStep;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Number of scheduled (durable) retries already used on the current step. */
    @Column(nullable = false)
    private int attempt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Fencing token: only the worker holding this token may write progress. */
    @Column(name = "lease_owner")
    private UUID leaseOwner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected SagaInstance() {
    }

    static SagaInstance start(UUID id, String idempotencyKey, String requestHash, String customerId,
                              List<SagaItem> items, Instant now) {
        SagaInstance s = new SagaInstance();
        s.id = id;
        s.idempotencyKey = idempotencyKey;
        s.requestHash = requestHash;
        s.customerId = customerId;
        s.items = new ArrayList<>(items);
        s.totalAmount = items.stream().map(SagaItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        s.status = SagaStatus.STARTED;
        s.currentStep = SagaStep.values()[0];
        s.attempt = 0;
        s.nextAttemptAt = now;
        s.createdAt = s.updatedAt = now;
        return s;
    }

    // ------------------------------------------------------------------ lease

    boolean isLeasedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    boolean isLeaseOwner(UUID token) {
        return Objects.equals(leaseOwner, token);
    }

    void acquireLease(UUID token, Instant until) {
        this.leaseOwner = token;
        this.lockedUntil = until;
    }

    void releaseLease() {
        this.leaseOwner = null;
        this.lockedUntil = null;
    }

    // ------------------------------------------------------------------ transitions

    void advanceTo(SagaStep next, Instant now) {
        moveTo(SagaStatus.STARTED, now);
        this.currentStep = next;
        resetAttempts(now);
    }

    void complete(Instant now) {
        moveTo(SagaStatus.COMPLETED, now);
        releaseLease();
    }

    /** Starts undoing steps, beginning with {@code from}. With nothing to undo, the saga is already COMPENSATED. */
    void beginCompensation(SagaStep from, String reason, Instant now) {
        this.failureReason = reason;
        if (from == null) {
            markCompensated(now);
            return;
        }
        moveTo(SagaStatus.COMPENSATING, now);
        this.currentStep = from;
        resetAttempts(now);
    }

    void compensateNext(SagaStep previous, Instant now) {
        moveTo(SagaStatus.COMPENSATING, now);
        this.currentStep = previous;
        resetAttempts(now);
    }

    void markCompensated(Instant now) {
        moveTo(SagaStatus.COMPENSATED, now);
        releaseLease();
    }

    void fail(String reason, Instant now) {
        moveTo(SagaStatus.FAILED, now);
        this.failureReason = reason;
        releaseLease();
    }

    void scheduleRetry(Instant at, Instant now) {
        this.attempt++;
        this.nextAttemptAt = at;
        this.updatedAt = now;
        releaseLease();
    }

    private void resetAttempts(Instant now) {
        this.attempt = 0;
        this.nextAttemptAt = now;
    }

    private void moveTo(SagaStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Saga " + id + " cannot move from " + status + " to " + target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------ getters

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<SagaItem> getItems() {
        return List.copyOf(items);
    }

    public SagaStatus getStatus() {
        return status;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
