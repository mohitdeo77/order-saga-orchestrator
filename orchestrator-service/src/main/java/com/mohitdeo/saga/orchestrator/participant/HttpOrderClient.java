package com.mohitdeo.saga.orchestrator.participant;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mohitdeo.saga.orchestrator.config.AppConfig.ParticipantRestClients;
import com.mohitdeo.saga.orchestrator.saga.SagaItem;

@Component
public class HttpOrderClient implements OrderClient {

    record Line(String sku, int quantity, BigDecimal unitPrice) {
    }

    record CreateOrder(UUID sagaId, String customerId, List<Line> items) {
    }

    record RejectOrder(String reason) {
    }

    private final RestClient rest;

    @Autowired
    public HttpOrderClient(ParticipantRestClients clients) {
        this(clients.orders());
    }

    HttpOrderClient(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public StepOutcome createOrder(UUID sagaId, String customerId, List<SagaItem> items) {
        List<Line> lines = items.stream().map(i -> new Line(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList();
        return HttpCalls.post(rest, "/api/orders", new CreateOrder(sagaId, customerId, lines));
    }

    @Override
    public StepOutcome approveOrder(UUID sagaId) {
        return HttpCalls.post(rest, "/api/orders/{sagaId}/approve", null, sagaId);
    }

    @Override
    public StepOutcome rejectOrder(UUID sagaId, String reason) {
        return HttpCalls.post(rest, "/api/orders/{sagaId}/reject", new RejectOrder(reason), sagaId);
    }
}
