package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.payment.application.port.PaymentGateway.GatewayResult;
import ro.midra.ticketing.payment.application.port.PaymentGateway.Outcome;
import ro.midra.ticketing.payment.domain.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static ro.midra.ticketing.payment.domain.PaymentOperation.*;

/** Decides durable continuation and compensation; never performs external I/O. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSaga {
    static final int MAX_RETRIES = 6;
    private final OutboxEventWriter outbox;

    public void request(PaymentAggregate payment, PaymentOperation operation, String scenario, LocalDateTime now) {
        String requestId = UUID.randomUUID().toString();
        switch (operation) {
            case INIT -> payment.initRequested(requestId, now);
            case CONFIRM -> payment.confirmRequested(requestId, now);
            case INQUIRY -> payment.inquiryRequested(requestId, now);
            case REFUND -> payment.refundRequested(requestId, now);
        }
        var command = new PaymentCommand(payment.state().paymentId(), requestId, operation, scenario);
        outbox.write("PaymentCommand", command.paymentId().toString(), command.messageType(), command);
    }

    public void onResult(PaymentAggregate payment, PaymentCommand command, GatewayResult result, LocalDateTime now) {
        PaymentOperation inquiryTarget = payment.state().targetOperation();
        PaymentOperation resolvedOperation = command.operation() == INQUIRY ? inquiryTarget : command.operation();
        boolean success = result.outcome() == Outcome.SUCCESS
                || (resolvedOperation == REFUND && result.outcome() == Outcome.REFUND_SUCCESS);
        boolean unknown = result.outcome() == Outcome.TIMEOUT || result.outcome() == Outcome.UNKNOWN;
        String reason = result.outcome() + ": " + result.detail();
        String reference = result.providerReference() != null ? result.providerReference() : payment.state().providerReference();
        log.info("paymentId={} reservationId={} operation={} retry={} providerReference={} outcome={}",
                payment.state().paymentId(), payment.state().reservationId(), command.operation(),
                payment.state().retryCount(), reference, result.outcome());
        if (unknown) {
            unresolved(payment, command.operation(), reason, reference, now);
            return;
        }
        switch (command.operation()) {
            case INIT -> { if (success) payment.initSucceeded(reference, now); else payment.initFailed(reason, now); }
            case CONFIRM -> { if (success) payment.confirmSucceeded(reference, now); else payment.confirmFailed(reason, now); }
            case REFUND -> { if (success) payment.refundSucceeded(reference, now); else payment.refundFailed(reason, now); }
            case INQUIRY -> payment.inquiryResolved(success, reference, reason, now);
        }
        switch (payment.state().status()) {
            case AWAITING_CONFIRM -> request(payment, CONFIRM, command.scenarioHint(), now);
            case REFUND_PENDING -> {
                if (command.operation() == REFUND || inquiryTarget == REFUND) retry(payment, reason, now);
                else request(payment, REFUND, command.scenarioHint(), now);
            }
            case CONFIRMED -> integration(payment, "RESERVATION_PAYMENT_CONFIRMED");
            case INIT_FAILED, REFUNDED -> integration(payment, "RESERVATION_PAYMENT_FAILED");
            default -> { }
        }
    }
    public void unresolved(PaymentAggregate payment, PaymentOperation operation, String reason, String reference, LocalDateTime now) {
        switch (operation) {
            case INIT -> payment.initTimedOut(reason, reference, now);
            case CONFIRM -> payment.confirmTimedOut(reason, now);
            case REFUND -> payment.refundTimedOut(reason, now);
            case INQUIRY -> payment.inquiryTimedOut(reason, now);
        }
        if (operation == INQUIRY) retry(payment, reason, now);
        else payment.retryScheduled(payment.state().retryCount(), now.plusSeconds(30), reason, now);
    }
    private void retry(PaymentAggregate payment, String reason, LocalDateTime now) {
        int next = payment.state().retryCount() + 1;
        if (next > MAX_RETRIES) {
            String intervention = payment.state().targetOperation() == REFUND || payment.state().status() == PaymentStatus.REFUND_PENDING
                    ? "Customer may have been charged; manual refund required"
                    : "Provider outcome unresolved; customer may have been charged; manual inquiry required";
            payment.markStuck(intervention + " after " + MAX_RETRIES + " retries: " + reason, now);
            log.error("paymentId={} reservationId={} STUCK reason={}", payment.state().paymentId(), payment.state().reservationId(), intervention);
        } else payment.retryScheduled(next, now.plusSeconds(Math.min(600, 30L * (1L << next))), reason, now);
    }
    private void integration(PaymentAggregate payment, String type) {
        outbox.write("PaymentIntegration", payment.state().paymentId().toString(), type,
                new ReservationPaymentMessage(payment.state().paymentId()));
    }
    public record ReservationPaymentMessage(Long paymentId) {}
}
