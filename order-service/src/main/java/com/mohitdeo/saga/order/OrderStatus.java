package com.mohitdeo.saga.order;

/**
 * PENDING ──► APPROVED   (saga completed)
 *    └──────► REJECTED   (saga compensated)
 */
public enum OrderStatus {
    PENDING,
    APPROVED,
    REJECTED
}
