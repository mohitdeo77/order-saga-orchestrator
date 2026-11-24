package com.mohitdeo.saga.orchestrator.saga;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row per step attempt. Rows are only ever appended, giving a full audit trail of each saga. */
@Entity
@Table(name = "saga_step_log")
public class SagaStepLog {

    public enum Phase { EXECUTE, COMPENSATE }

    public enum Result { SUCCEEDED, REJECTED, RETRY_SCHEDULED, FAILED }

    private static final int MAX_DETAIL = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Phase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SagaStepLog() {
    }

    SagaStepLog(UUID sagaId, SagaStep step, Phase phase, Result result, String detail, Instant createdAt) {
        this.sagaId = sagaId;
        this.step = step;
        this.phase = phase;
        this.result = result;
        this.detail = detail == null || detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
        this.createdAt = createdAt;
    }

    public SagaStep getStep() {
        return step;
    }

    public Phase getPhase() {
        return phase;
    }

    public Result getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
