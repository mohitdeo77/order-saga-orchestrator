package com.mohitdeo.saga.inventory;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohitdeo.saga.inventory.InventoryDtos.ProductView;
import com.mohitdeo.saga.inventory.InventoryDtos.ReservationView;
import com.mohitdeo.saga.inventory.InventoryDtos.ReserveResult;
import com.mohitdeo.saga.inventory.InventoryDtos.ReserveStockRequest;

@RestController
@RequestMapping("/api/inventory")
class InventoryController {

    private final InventoryService inventoryService;

    InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reservations")
    ResponseEntity<ReservationView> reserve(@Valid @RequestBody ReserveStockRequest request) {
        ReserveResult result = inventoryService.reserve(request.sagaId(), request.items());
        if (!result.created()) {
            return ResponseEntity.ok(result.reservation());
        }
        return ResponseEntity.created(URI.create("/api/inventory/reservations/" + request.sagaId()))
                .body(result.reservation());
    }

    @PostMapping("/reservations/{sagaId}/release")
    ReservationView release(@PathVariable UUID sagaId) {
        return inventoryService.release(sagaId);
    }

    @GetMapping("/products/{sku}")
    ProductView product(@PathVariable String sku) {
        return inventoryService.product(sku);
    }
}
