package com.mohitdeo.saga.payment;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxAmount             charges above this amount are declined (simulates a card limit)
 * @param simulatedFailureRate  share of requests (0.0 to 1.0) answered with 503 before any work is
 *                              done, to show the orchestrator's retries and recovery in a demo
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(BigDecimal maxAmount, double simulatedFailureRate) {
}
