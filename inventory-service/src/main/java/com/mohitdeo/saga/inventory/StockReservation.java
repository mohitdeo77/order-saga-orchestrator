package com.mohitdeo.saga.inventory;

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

/** Stock held for one saga. The saga id is the primary key, so each saga can reserve stock only once. */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_reservation_items", joinColumns = @JoinColumn(name = "saga_id"))
    private List<ReservedItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StockReservation() {
    }

    static StockReservation reserved(UUID sagaId, List<ReservedItem> items) {
        StockReservation r = new StockReservation();
        r.sagaId = sagaId;
        r.items = new ArrayList<>(items);
        r.status = ReservationStatus.RESERVED;
        r.createdAt = r.updatedAt = Instant.now();
        return r;
    }

    /** Release that arrives before any reservation exists. It blocks a late reserve call for the same saga. */
    static StockReservation releasedTombstone(UUID sagaId) {
        StockReservation r = new StockReservation();
        r.sagaId = sagaId;
        r.status = ReservationStatus.RELEASED;
        r.createdAt = r.updatedAt = Instant.now();
        return r;
    }

    void markReleased() {
        status = ReservationStatus.RELEASED;
        updatedAt = Instant.now();
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservedItem> getItems() {
        return List.copyOf(items);
    }
}
