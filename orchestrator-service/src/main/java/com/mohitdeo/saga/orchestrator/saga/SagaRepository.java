package com.mohitdeo.saga.orchestrator.saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SagaInstance s where s.id = :id")
    Optional<SagaInstance> findByIdForUpdate(UUID id);

    /** In-flight sagas whose next attempt is due and which no live worker currently leases. */
    @Query("""
            select s.id from SagaInstance s
            where s.status in (com.mohitdeo.saga.orchestrator.saga.SagaStatus.STARTED,
                               com.mohitdeo.saga.orchestrator.saga.SagaStatus.COMPENSATING)
              and s.nextAttemptAt <= :now
              and (s.lockedUntil is null or s.lockedUntil <= :now)
            order by s.nextAttemptAt
            """)
    List<UUID> findDueForRecovery(Instant now, Pageable page);
}
