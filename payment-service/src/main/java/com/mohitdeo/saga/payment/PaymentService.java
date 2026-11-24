package com.mohitdeo.saga.payment;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.saga.payment.PaymentDtos.ChargeRequest;
import com.mohitdeo.saga.payment.PaymentDtos.ChargeResult;
import com.mohitdeo.saga.payment.PaymentDtos.PaymentView;

/**
 * Charges and refunds, both idempotent per saga id.
 * <p>
 * A decline is stored instead of only being returned. If the orchestrator retries a charge
 * after a timeout, it gets the same decision back and is never charged on the second try.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProperties props;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, PaymentProperties props, TransactionTemplate tx) {
        this.payments = payments;
        this.props = props;
        this.tx = tx;
    }

    public ChargeResult charge(ChargeRequest request) {
        try {
            return tx.execute(status -> payments.findForUpdate(request.sagaId())
                    .map(existing -> new ChargeResult(PaymentView.from(existing), false))
                    .orElseGet(() -> {
                        Payment payment = request.amount().compareTo(props.maxAmount()) > 0
                                ? Payment.declined(request.sagaId(), request.customerId(), request.amount(),
                                        "Amount exceeds limit of " + props.maxAmount())
                                : Payment.charged(request.sagaId(), request.customerId(), request.amount());
                        return new ChargeResult(PaymentView.from(payments.saveAndFlush(payment)), true);
                    }));
        } catch (DataIntegrityViolationException e) {
            Payment existing = payments.findById(request.sagaId()).orElseThrow(() -> e);
            return new ChargeResult(PaymentView.from(existing), false);
        }
    }

    /** Compensating action. Refunds a captured charge; anything else is already a no-op. */
    public PaymentView refund(UUID sagaId) {
        try {
            return tx.execute(status -> {
                Payment payment = payments.findForUpdate(sagaId)
                        .orElseGet(() -> payments.saveAndFlush(Payment.voidedTombstone(sagaId)));
                if (payment.getStatus() == PaymentStatus.CHARGED) {
                    payment.refund();
                }
                return PaymentView.from(payment);
            });
        } catch (DataIntegrityViolationException e) {
            return refund(sagaId);
        }
    }

    public PaymentView get(UUID sagaId) {
        return payments.findById(sagaId).map(PaymentView::from)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, "Payment not found",
                        "No payment for saga " + sagaId));
    }
}
