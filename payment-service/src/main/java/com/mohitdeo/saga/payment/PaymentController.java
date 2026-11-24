package com.mohitdeo.saga.payment;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mohitdeo.saga.payment.PaymentDtos.ChargeRequest;
import com.mohitdeo.saga.payment.PaymentDtos.ChargeResult;
import com.mohitdeo.saga.payment.PaymentDtos.PaymentView;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties props;

    PaymentController(PaymentService paymentService, PaymentProperties props) {
        this.paymentService = paymentService;
        this.props = props;
    }

    @PostMapping
    ResponseEntity<PaymentView> charge(@Valid @RequestBody ChargeRequest request) {
        maybeSimulateOutage();
        ChargeResult result = paymentService.charge(request);
        PaymentView payment = result.payment();

        return switch (payment.status()) {
            case CHARGED -> result.created()
                    ? ResponseEntity.created(URI.create("/api/payments/" + payment.sagaId())).body(payment)
                    : ResponseEntity.ok(payment);
            case DECLINED -> throw new PaymentException(HttpStatus.UNPROCESSABLE_CONTENT, "Payment declined",
                    payment.declineReason());
            case REFUNDED, VOIDED -> throw new PaymentException(HttpStatus.CONFLICT, "Payment already compensated",
                    "Payment for saga " + payment.sagaId() + " is " + payment.status());
        };
    }

    @PostMapping("/{sagaId}/refund")
    PaymentView refund(@PathVariable UUID sagaId) {
        maybeSimulateOutage();
        return paymentService.refund(sagaId);
    }

    @GetMapping("/{sagaId}")
    PaymentView get(@PathVariable UUID sagaId) {
        return paymentService.get(sagaId);
    }

    private void maybeSimulateOutage() {
        if (props.simulatedFailureRate() > 0 && ThreadLocalRandom.current().nextDouble() < props.simulatedFailureRate()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated outage",
                    "Payment provider temporarily unavailable");
        }
    }
}
