package com.mohitdeo.saga.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull UUID sagaId,
            @NotBlank String customerId,
            @NotEmpty List<@Valid Line> items) {
    }

    public record Line(@NotBlank String sku, @Min(1) int quantity, @NotNull @DecimalMin("0.00") BigDecimal unitPrice) {
    }

    public record RejectOrderRequest(String reason) {
    }

    public record OrderView(UUID orderId, UUID sagaId, String customerId, BigDecimal totalAmount,
                            OrderStatus status, String rejectionReason, List<Line> items) {

        static OrderView from(Order o) {
            return new OrderView(o.getId(), o.getSagaId(), o.getCustomerId(), o.getTotalAmount(), o.getStatus(),
                    o.getRejectionReason(),
                    o.getLines().stream().map(l -> new Line(l.getSku(), l.getQuantity(), l.getUnitPrice())).toList());
        }
    }

    public record CreateResult(OrderView order, boolean created) {
    }
}
