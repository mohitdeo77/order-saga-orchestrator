package com.mohitdeo.saga.orchestrator.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohitdeo.saga.orchestrator.saga.SagaItem;
import com.mohitdeo.saga.orchestrator.saga.SagaService;
import com.mohitdeo.saga.orchestrator.saga.SagaService.StartResult;
import com.mohitdeo.saga.orchestrator.saga.SagaView;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final SagaService sagaService;

    OrderController(SagaService sagaService) {
        this.sagaService = sagaService;
    }

    /**
     * Places an order. Returns 202 Accepted: fulfilment continues in the background,
     * and the client polls the Location URL for the result.
     */
    @PostMapping
    ResponseEntity<SagaView> placeOrder(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        StartResult result = sagaService.start(request.customerId(),
                request.items().stream().map(i -> new SagaItem(i.sku(), i.quantity(), i.unitPrice())).toList(),
                idempotencyKey);

        if (!result.created()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.saga());
        }
        return ResponseEntity.accepted()
                .location(URI.create("/api/orders/" + result.saga().sagaId()))
                .body(result.saga());
    }

    @GetMapping("/{sagaId}")
    SagaView get(@PathVariable UUID sagaId) {
        return sagaService.get(sagaId);
    }
}
