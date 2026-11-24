package com.mohitdeo.saga.order;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.saga.order.OrderDtos.CreateOrderRequest;
import com.mohitdeo.saga.order.OrderDtos.CreateResult;
import com.mohitdeo.saga.order.OrderDtos.OrderView;

/**
 * Every operation is idempotent and keyed by saga id, because the orchestrator retries calls
 * that time out. A repeated call returns the current result and changes nothing.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, TransactionTemplate tx) {
        this.orders = orders;
        this.tx = tx;
    }

    public CreateResult create(CreateOrderRequest request) {
        try {
            return tx.execute(status -> orders.findBySagaId(request.sagaId())
                    .map(existing -> new CreateResult(requireNotRejected(existing), false))
                    .orElseGet(() -> {
                        Order order = Order.pending(request.sagaId(), request.customerId(),
                                request.items().stream()
                                        .map(i -> new OrderLine(i.sku(), i.quantity(), i.unitPrice()))
                                        .toList());
                        return new CreateResult(OrderView.from(orders.saveAndFlush(order)), true);
                    }));
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate committed first: return its result
            Order existing = orders.findBySagaId(request.sagaId()).orElseThrow(() -> e);
            return new CreateResult(requireNotRejected(existing), false);
        }
    }

    public OrderView approve(UUID sagaId) {
        return tx.execute(status -> {
            Order order = orders.findBySagaIdForUpdate(sagaId).orElseThrow(() -> new OrderNotFoundException(sagaId));
            switch (order.getStatus()) {
                case APPROVED -> { /* already approved: idempotent */ }
                case REJECTED -> throw new OrderConflictException("Order for saga " + sagaId + " was rejected");
                case PENDING -> order.approve();
            }
            return OrderView.from(order);
        });
    }

    /** Compensating action. Safe to repeat, and safe to call before the order exists. */
    public OrderView reject(UUID sagaId, String reason) {
        try {
            return tx.execute(status -> {
                Order order = orders.findBySagaIdForUpdate(sagaId)
                        .orElseGet(() -> orders.saveAndFlush(Order.rejectedTombstone(sagaId, reason)));
                switch (order.getStatus()) {
                    case REJECTED -> { /* already rejected: idempotent */ }
                    case APPROVED -> throw new OrderConflictException(
                            "Order for saga " + sagaId + " is already approved and cannot be rejected");
                    case PENDING -> order.reject(reason);
                }
                return OrderView.from(order);
            });
        } catch (DataIntegrityViolationException e) {
            // The order was created at the same moment; retry once now that it exists
            return reject(sagaId, reason);
        }
    }

    public OrderView get(UUID sagaId) {
        return orders.findBySagaId(sagaId).map(OrderView::from).orElseThrow(() -> new OrderNotFoundException(sagaId));
    }

    private static OrderView requireNotRejected(Order order) {
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new OrderConflictException("Order for saga " + order.getSagaId() + " was already rejected");
        }
        return OrderView.from(order);
    }
}
