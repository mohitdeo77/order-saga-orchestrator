package com.mohitdeo.saga.orchestrator.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.mohitdeo.saga.orchestrator.participant.StepOutcome;
import com.mohitdeo.saga.orchestrator.support.FakeParticipants;
import com.mohitdeo.saga.orchestrator.support.TestInfrastructure;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
class OrderApiTest {

    private static final String BODY = """
            {"customerId":"cust-7","items":[{"sku":"LAPTOP-15","quantity":1,"unitPrice":999.99}]}
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    FakeParticipants participants;

    @BeforeEach
    void reset() {
        participants.reset();
    }

    @Test
    void placeOrderReturnsAcceptedWithLocationAndReplaysOnRetry() throws Exception {
        String json = mvc.perform(placeOrder("api-key-1", BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        String sagaId = JsonPath.read(json, "$.sagaId");

        mvc.perform(placeOrder("api-key-1", BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.sagaId").value(sagaId));

        mvc.perform(get("/api/orders/{id}", sagaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.history.length()").value(4))
                .andExpect(jsonPath("$.history[0].step").value("CREATE_ORDER"));
    }

    @Test
    void compensatedOrderExposesReasonAndHistory() throws Exception {
        participants.script(FakeParticipants.CHARGE, StepOutcome.rejected("422 Payment declined"));

        mvc.perform(placeOrder("api-key-2", BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPENSATED"))
                .andExpect(jsonPath("$.failureReason").value("PROCESS_PAYMENT rejected: 422 Payment declined"))
                .andExpect(jsonPath("$.history[3].phase").value("COMPENSATE"));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keyReuseWithDifferentBodyIsUnprocessable() throws Exception {
        mvc.perform(placeOrder("api-key-3", BODY)).andExpect(status().isAccepted());
        mvc.perform(placeOrder("api-key-3", BODY.replace("cust-7", "cust-8")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        mvc.perform(placeOrder("api-key-4", "{\"customerId\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mvc.perform(get("/api/orders/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    private static RequestBuilder placeOrder(String key, String body) {
        return post("/api/orders").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
