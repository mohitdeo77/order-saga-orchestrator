package com.mohitdeo.saga.orchestrator.participant;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mohitdeo.saga.orchestrator.config.AppConfig.ParticipantRestClients;
import com.mohitdeo.saga.orchestrator.saga.SagaItem;

@Component
public class HttpInventoryClient implements InventoryClient {

    record Item(String sku, int quantity) {
    }

    record ReserveStock(UUID sagaId, List<Item> items) {
    }

    private final RestClient rest;

    @Autowired
    public HttpInventoryClient(ParticipantRestClients clients) {
        this(clients.inventory());
    }

    HttpInventoryClient(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public StepOutcome reserveStock(UUID sagaId, List<SagaItem> items) {
        List<Item> body = items.stream().map(i -> new Item(i.getSku(), i.getQuantity())).toList();
        return HttpCalls.post(rest, "/api/inventory/reservations", new ReserveStock(sagaId, body));
    }

    @Override
    public StepOutcome releaseStock(UUID sagaId) {
        return HttpCalls.post(rest, "/api/inventory/reservations/{sagaId}/release", null, sagaId);
    }
}
