package com.mohitdeo.saga.order;

/** The request conflicts with the order's current state. Mapped to 409, which the orchestrator treats as non-retriable. */
public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }
}
