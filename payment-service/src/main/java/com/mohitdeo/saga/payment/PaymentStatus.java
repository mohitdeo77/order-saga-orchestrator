package com.mohitdeo.saga.payment;

public enum PaymentStatus {
    /** Money captured. */
    CHARGED,
    /** Charge refused (business rule). Stored, so a retried charge gets the same answer. */
    DECLINED,
    /** Compensated: captured money returned. */
    REFUNDED,
    /** Refund arrived before any charge. Tombstone that blocks a late charge. */
    VOIDED
}
