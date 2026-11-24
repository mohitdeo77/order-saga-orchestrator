package com.mohitdeo.saga.order;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID sagaId) {
        super("No order for saga " + sagaId);
    }
}
