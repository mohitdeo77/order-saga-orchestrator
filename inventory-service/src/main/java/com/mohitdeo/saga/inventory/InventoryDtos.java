package com.mohitdeo.saga.inventory;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record ReserveStockRequest(@NotNull UUID sagaId, @NotEmpty List<@Valid Item> items) {
    }

    public record Item(@NotBlank String sku, @Min(1) int quantity) {
    }

    public record ReservationView(UUID sagaId, ReservationStatus status, List<Item> items) {
        static ReservationView from(StockReservation r) {
            return new ReservationView(r.getSagaId(), r.getStatus(),
                    r.getItems().stream().map(i -> new Item(i.getSku(), i.getQuantity())).toList());
        }
    }

    public record ReserveResult(ReservationView reservation, boolean created) {
    }

    public record ProductView(String sku, String name, int availableQuantity) {
        static ProductView from(Product p) {
            return new ProductView(p.getSku(), p.getName(), p.getAvailableQuantity());
        }
    }
}
