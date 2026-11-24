package com.mohitdeo.saga.payment;

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
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void chargeIsIdempotent() throws Exception {
        UUID sagaId = UUID.randomUUID();

        mvc.perform(charge(sagaId, "199.99")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHARGED"));
        mvc.perform(charge(sagaId, "199.99")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGED"));
    }

    @Test
    void declineIsRememberedSoRetriesGetTheSameAnswer() throws Exception {
        UUID sagaId = UUID.randomUUID();

        mvc.perform(charge(sagaId, "9999.00")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Payment declined"));
        mvc.perform(charge(sagaId, "9999.00")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void refundReversesChargeAndIsIdempotent() throws Exception {
        UUID sagaId = UUID.randomUUID();
        mvc.perform(charge(sagaId, "50.00"));

        mvc.perform(post("/api/payments/{id}/refund", sagaId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(post("/api/payments/{id}/refund", sagaId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundOfDeclinedPaymentIsANoOp() throws Exception {
        UUID sagaId = UUID.randomUUID();
        mvc.perform(charge(sagaId, "9999.00"));

        mvc.perform(post("/api/payments/{id}/refund", sagaId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void refundBeforeChargeBlocksTheLateCharge() throws Exception {
        UUID sagaId = UUID.randomUUID();

        mvc.perform(post("/api/payments/{id}/refund", sagaId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
        mvc.perform(charge(sagaId, "50.00")).andExpect(status().isConflict());

        mvc.perform(get("/api/payments/{id}", sagaId)).andExpect(jsonPath("$.status").value("VOIDED"));
    }

    private static RequestBuilder charge(UUID sagaId, String amount) {
        return post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sagaId\":\"%s\",\"customerId\":\"cust-1\",\"amount\":%s}".formatted(sagaId, amount));
    }
}
