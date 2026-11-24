package com.mohitdeo.saga.orchestrator.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Checks how HTTP results map to Success, Rejected or Transient: the contract the saga relies on. */
class HttpParticipantClientsTest {

    private static final UUID SAGA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    MockRestServiceServer server;
    HttpPaymentClient payments;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment");
        server = MockRestServiceServer.bindTo(builder).build();
        payments = new HttpPaymentClient(builder.build());
    }

    @Test
    void successResponseIsSuccess() {
        server.expect(requestTo("http://payment/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"sagaId\":\"" + SAGA + "\",\"customerId\":\"c1\",\"amount\":10.50}"))
                .andRespond(withSuccess());

        assertThat(payments.charge(SAGA, "c1", new BigDecimal("10.50"))).isInstanceOf(StepOutcome.Success.class);
        server.verify();
    }

    @Test
    void businessErrorIsRejectedWithProblemDetail() {
        server.expect(requestTo("http://payment/api/payments"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Payment declined\",\"status\":422,\"detail\":\"Amount exceeds limit\"}"));

        StepOutcome outcome = payments.charge(SAGA, "c1", BigDecimal.TEN);

        assertThat(outcome).isInstanceOf(StepOutcome.Rejected.class);
        assertThat(((StepOutcome.Rejected) outcome).reason()).isEqualTo("422 Amount exceeds limit");
    }

    @Test
    void serverErrorIsTransient() {
        server.expect(requestTo("http://payment/api/payments/" + SAGA + "/refund"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(payments.refund(SAGA)).isInstanceOf(StepOutcome.Transient.class);
    }

    @Test
    void tooManyRequestsIsTransient() {
        server.expect(requestTo("http://payment/api/payments")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(payments.charge(SAGA, "c1", BigDecimal.ONE)).isInstanceOf(StepOutcome.Transient.class);
    }

    @Test
    void timeoutIsTransient() {
        server.expect(requestTo("http://payment/api/payments"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(payments.charge(SAGA, "c1", BigDecimal.ONE)).isInstanceOf(StepOutcome.Transient.class);
    }

    @Test
    void connectionRefusedIsTransient() {
        server.expect(requestTo("http://payment/api/payments"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThat(payments.charge(SAGA, "c1", BigDecimal.ONE)).isInstanceOf(StepOutcome.Transient.class);
    }
}
