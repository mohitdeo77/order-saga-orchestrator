package com.mohitdeo.saga.order;

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

import com.mohitdeo.saga.order.OrderDtos.CreateOrderRequest;
import com.mohitdeo.saga.order.OrderDtos.CreateResult;
import com.mohitdeo.saga.order.OrderDtos.OrderView;
import com.mohitdeo.saga.order.OrderDtos.RejectOrderRequest;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderView> create(@Valid @RequestBody CreateOrderRequest request) {
        CreateResult result = orderService.create(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + request.sagaId())).body(result.order());
    }

    @GetMapping("/{sagaId}")
    OrderView get(@PathVariable UUID sagaId) {
        return orderService.get(sagaId);
    }

    @PostMapping("/{sagaId}/approve")
    OrderView approve(@PathVariable UUID sagaId) {
        return orderService.approve(sagaId);
    }

    @PostMapping("/{sagaId}/reject")
    OrderView reject(@PathVariable UUID sagaId, @RequestBody(required = false) RejectOrderRequest request) {
        return orderService.reject(sagaId, request == null ? null : request.reason());
    }
}
