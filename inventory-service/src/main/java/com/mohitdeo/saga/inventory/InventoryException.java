package com.mohitdeo.saga.inventory;

import org.springframework.http.HttpStatus;

public class InventoryException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    private InventoryException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    static InventoryException insufficientStock(String detail) {
        return new InventoryException(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient stock", detail);
    }

    static InventoryException unknownProducts(String detail) {
        return new InventoryException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown product", detail);
    }

    static InventoryException alreadyReleased(String detail) {
        return new InventoryException(HttpStatus.CONFLICT, "Reservation already released", detail);
    }

    static InventoryException notFound(String detail) {
        return new InventoryException(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    HttpStatus status() {
        return status;
    }

    String title() {
        return title;
    }
}
