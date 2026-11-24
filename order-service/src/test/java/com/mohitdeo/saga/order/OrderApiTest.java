package com.mohitdeo.saga.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createIsIdempotentPerSaga() throws Exception {
        UUID sagaId = UUID.randomUUID();

        mvc.perform(create(sagaId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(250.0));
        mvc.perform(create(sagaId)).andExpect(status().isOk());
    }

    @Test
    void approveMovesPendingToApprovedAndIsIdempotent() throws Exception {
        UUID sagaId = UUID.randomUUID();
        mvc.perform(create(sagaId)).andExpect(status().isCreated());

        mvc.perform(post("/api/orders/{id}/approve", sagaId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/orders/{id}/approve", sagaId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectIsIdempotent() throws Exception {
        UUID sagaId = UUID.randomUUID();
        mvc.perform(create(sagaId)).andExpect(status().isCreated());

        mvc.perform(reject(sagaId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(reject(sagaId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectBeforeCreateLeavesTombstoneThatBlocksLateCreate() throws Exception {
        UUID sagaId = UUID.randomUUID();

        // The create call timed out at the orchestrator and compensation ran first
        mvc.perform(reject(sagaId)).andExpect(status().isOk());
        // Then the delayed create finally arrives: it must not bring the order back
        mvc.perform(create(sagaId)).andExpect(status().isConflict());

        mvc.perform(get("/api/orders/{id}", sagaId)).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void approvedOrderCannotBeRejected() throws Exception {
        UUID sagaId = UUID.randomUUID();
        mvc.perform(create(sagaId));
        mvc.perform(post("/api/orders/{id}/approve", sagaId));

        mvc.perform(reject(sagaId)).andExpect(status().isConflict());
    }

    @Test
    void invalidRequestIsRejected() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sagaId\":null,\"customerId\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.RequestBuilder create(UUID sagaId) {
        return post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"sagaId":"%s","customerId":"cust-1",
                 "items":[{"sku":"BOOK-1","quantity":2,"unitPrice":100.00},
                          {"sku":"PEN-1","quantity":1,"unitPrice":50.00}]}
                """.formatted(sagaId));
    }

    private static org.springframework.test.web.servlet.RequestBuilder reject(UUID sagaId) {
        return post("/api/orders/{id}/reject", sagaId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"payment declined\"}");
    }
}
