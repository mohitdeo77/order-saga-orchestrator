package com.mohitdeo.saga.payment;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    PaymentException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    HttpStatus status() {
        return status;
    }

    String title() {
        return title;
    }
}
