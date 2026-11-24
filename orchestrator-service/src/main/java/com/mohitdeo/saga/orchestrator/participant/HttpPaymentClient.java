package com.mohitdeo.saga.orchestrator.participant;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mohitdeo.saga.orchestrator.config.AppConfig.ParticipantRestClients;

@Component
public class HttpPaymentClient implements PaymentClient {

    record Charge(UUID sagaId, String customerId, BigDecimal amount) {
    }

    private final RestClient rest;

    @Autowired
    public HttpPaymentClient(ParticipantRestClients clients) {
        this(clients.payments());
    }

    HttpPaymentClient(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public StepOutcome charge(UUID sagaId, String customerId, BigDecimal amount) {
        return HttpCalls.post(rest, "/api/payments", new Charge(sagaId, customerId, amount));
    }

    @Override
    public StepOutcome refund(UUID sagaId) {
        return HttpCalls.post(rest, "/api/payments/{sagaId}/refund", null, sagaId);
    }
}
