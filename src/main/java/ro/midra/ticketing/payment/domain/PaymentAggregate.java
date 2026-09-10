package ro.midra.ticketing.payment.domain;

import ro.midra.ticketing.payment.domain.PaymentEvent.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ro.midra.ticketing.payment.domain.PaymentOperation.*;
import static ro.midra.ticketing.payment.domain.PaymentStatus.*;

/**
 * The only payment state machine. New facts and historical facts use the same apply method.
 */
public final class PaymentAggregate {
    private Long paymentId, reservationId, userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String operationId, providerReference, lastError, requestId;
    private PaymentOperation targetOperation, requestedOperation;
    private int retryCount;
    private long version;
    private boolean dispatched;
    private LocalDateTime nextRetryAt, createdAt, updatedAt;
    private final List<PaymentEvent> uncommitted = new ArrayList<>();

    private PaymentAggregate() {
    }

    public static PaymentAggregate start(Long id, Long reservationId, Long userId, BigDecimal amount,
                                         String operationId, LocalDateTime now) {
        require(id != null && reservationId != null && userId != null, "Payment identity is required");
        require(amount != null && amount.signum() > 0, "Payment amount must be positive");
        require(operationId != null && !operationId.isBlank(), "Operation ID is required");
        PaymentAggregate payment = new PaymentAggregate();
        payment.raise(new PaymentInitiated(id, reservationId, userId, amount, operationId, now));
        return payment;
    }

    public static PaymentAggregate rehydrate(List<PaymentEvent> events) {
        require(!events.isEmpty() && events.getFirst() instanceof PaymentInitiated, "Missing PaymentInitiated");
        PaymentAggregate payment = new PaymentAggregate();
        events.forEach(payment::apply);
        return payment;
    }

    public PaymentState state() {
        return new PaymentState(paymentId, reservationId, userId, amount, status, operationId,
                providerReference, targetOperation, retryCount, nextRetryAt, lastError, version,
                createdAt, updatedAt, requestId, requestedOperation, dispatched);
    }

    public long version() {
        return version;
    }

    public long committedVersion() {
        return version - uncommitted.size();
    }

    public List<PaymentEvent> getUncommittedEvents() {
        return List.copyOf(uncommitted);
    }

    public void markEventsCommitted() {
        uncommitted.clear();
    }

    public String idempotencyKey(PaymentOperation operation) {
        require(operation != INQUIRY, "Inquiry uses the target operation key");
        return operationId + "-" + operation.name();
    }

    public boolean accepts(String id, PaymentOperation operation) {
        return !terminal() && Objects.equals(requestId, id) && requestedOperation == operation;
    }

    public boolean terminal() {
        return status == CONFIRMED || status == INIT_FAILED || status == REFUNDED || status == STUCK;
    }

    public void initRequested(String id, LocalDateTime now) {
        requestAllowed(INIT_PENDING, id);
        raise(new PaymentInitRequested(id, now));
    }

    public void confirmRequested(String id, LocalDateTime now) {
        requestAllowed(AWAITING_CONFIRM, id);
        raise(new PaymentConfirmRequested(id, now));
    }

    public void refundRequested(String id, LocalDateTime now) {
        requestAllowed(REFUND_PENDING, id);
        raise(new PaymentRefundRequested(id, now));
    }

    public void inquiryRequested(String id, LocalDateTime now) {
        requestAllowed(INQUIRY_PENDING, id);
        require(targetOperation != null && targetOperation != INQUIRY, "Inquiry target is required");
        raise(new PaymentInquiryRequested(id, targetOperation, now));
    }

    public void providerCallStarted(String id, LocalDateTime deadline, LocalDateTime now) {
        require(requestId != null && requestId.equals(id) && !dispatched, "Request already dispatched or stale");
        require(deadline.isAfter(now), "Recovery deadline must be in the future");
        raise(new PaymentProviderCallStarted(id, deadline, now));
    }

    public void initSucceeded(String reference, LocalDateTime now) {
        resultAllowed(INIT);
        referenceRequired(reference);
        raise(new PaymentInitSucceeded(reference, now));
    }

    public void initFailed(String reason, LocalDateTime now) {
        resultAllowed(INIT);
        raise(new PaymentInitFailed(reason, now));
    }

    public void initTimedOut(String reason, String reference, LocalDateTime now) {
        resultAllowed(INIT);
        raise(new PaymentInitTimedOut(reason, reference, now));
    }

    public void confirmSucceeded(String reference, LocalDateTime now) {
        resultAllowed(CONFIRM);
        referenceRequired(reference);
        raise(new PaymentConfirmed(reference, now));
    }

    public void confirmFailed(String reason, LocalDateTime now) {
        resultAllowed(CONFIRM);
        raise(new PaymentConfirmFailed(reason, now));
    }

    public void confirmTimedOut(String reason, LocalDateTime now) {
        resultAllowed(CONFIRM);
        raise(new PaymentConfirmTimedOut(reason, now));
    }

    public void refundSucceeded(String reference, LocalDateTime now) {
        resultAllowed(REFUND);
        raise(new PaymentRefunded(reference, now));
    }

    public void refundFailed(String reason, LocalDateTime now) {
        resultAllowed(REFUND);
        raise(new PaymentRefundFailed(reason, now));
    }

    public void refundTimedOut(String reason, LocalDateTime now) {
        resultAllowed(REFUND);
        raise(new PaymentRefundTimedOut(reason, now));
    }

    public void inquiryTimedOut(String reason, LocalDateTime now) {
        require(status == INQUIRY_PENDING && requestedOperation == INQUIRY, "No inquiry is pending");
        raise(new PaymentInquiryTimedOut(reason, now));
    }

    public void inquiryResolved(boolean succeeded, String reference, String detail, LocalDateTime now) {
        require(status == INQUIRY_PENDING && requestedOperation == INQUIRY, "No inquiry is pending");
        PaymentOperation target = targetOperation;
        if (succeeded && target != REFUND) referenceRequired(reference);
        raise(new PaymentInquiryResolved(target, succeeded, reference, detail, now));
        // Explicit business outcome follows the reconciliation fact, in the same append.
        switch (target) {
            case INIT -> {
                if (succeeded) initSucceeded(reference, now);
                else initFailed(detail, now);
            }
            case CONFIRM -> {
                if (succeeded) confirmSucceeded(reference, now);
                else confirmFailed(detail, now);
            }
            case REFUND -> {
                if (succeeded) refundSucceeded(reference, now);
                else refundFailed(detail, now);
            }
            case INQUIRY -> throw new IllegalStateException("Cannot reconcile an inquiry");
        }
    }

    public void retryScheduled(int count, LocalDateTime at, String reason, LocalDateTime now) {
        require(!terminal() && (status == INQUIRY_PENDING || status == REFUND_PENDING), "Cannot retry this payment");
        require(count >= retryCount && count <= retryCount + 1 && at.isAfter(now), "Invalid retry schedule");
        raise(new PaymentRetryScheduled(count, at, reason, now));
    }

    public void markStuck(String reason, LocalDateTime now) {
        require(!terminal(), "Payment is terminal");
        raise(new PaymentMarkedStuck(reason, now));
    }

    private void requestAllowed(PaymentStatus expected, String id) {
        require(status == expected && requestId == null, "Invalid request in " + status);
        require(id != null && !id.isBlank(), "Request ID is required");
    }

    private void resultAllowed(PaymentOperation operation) {
        require(!terminal() && ((requestedOperation == operation && requestId != null)
                        || (status == INQUIRY_PENDING && targetOperation == operation && requestedOperation == null)),
                "No " + operation + " result is expected in " + status);
    }

    private static void referenceRequired(String reference) {
        require(reference != null && !reference.isBlank(), "Provider reference is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void raise(PaymentEvent event) {
        apply(event);
        uncommitted.add(event);
    }

    private void apply(PaymentEvent event) {
        switch (event) {
            case PaymentInitiated e -> {
                require(paymentId == null, "Payment was already initiated");
                paymentId = e.paymentId();
                reservationId = e.reservationId();
                userId = e.userId();
                amount = e.amount();
                operationId = e.operationId();
                status = INIT_PENDING;
                createdAt = e.occurredAt();
            }
            case PaymentLegacyStateImported e -> {
                PaymentState s = e.state();
                require(version == 1 && Objects.equals(paymentId, s.paymentId()), "Invalid legacy checkpoint");
                reservationId = s.reservationId();
                userId = s.userId();
                amount = s.amount();
                status = s.status();
                operationId = s.operationId();
                providerReference = s.providerReference();
                targetOperation = s.targetOperation();
                retryCount = s.retryCount();
                nextRetryAt = s.nextRetryAt();
                lastError = s.lastError();
                createdAt = s.createdAt();
                requestId = s.requestId();
                requestedOperation = s.requestedOperation();
                dispatched = s.dispatched();
            }
            case PaymentInitRequested e -> requested(e.requestId(), INIT);
            case PaymentConfirmRequested e -> requested(e.requestId(), CONFIRM);
            case PaymentRefundRequested e -> requested(e.requestId(), REFUND);
            case PaymentInquiryRequested e -> {
                targetOperation = e.targetOperation();
                requested(e.requestId(), INQUIRY);
            }
            case PaymentProviderCallStarted e -> {
                dispatched = true;
                nextRetryAt = e.recoverAfter();
            }
            case PaymentInitSucceeded e -> {
                providerReference = e.providerReference();
                completed(AWAITING_CONFIRM);
            }
            case PaymentConfirmed e -> {
                providerReference = e.providerReference();
                completed(CONFIRMED);
            }
            case PaymentRefunded e -> {
                if (e.providerReference() != null) providerReference = e.providerReference();
                completed(REFUNDED);
            }
            case PaymentInitFailed e -> {
                completed(INIT_FAILED);
                lastError = e.reason();
            }
            case PaymentConfirmFailed e -> {
                completed(REFUND_PENDING);
                lastError = e.reason();
            }
            case PaymentRefundFailed e -> {
                clearRequest();
                targetOperation = null;
                status = REFUND_PENDING;
                lastError = e.reason();
            }
            case PaymentInitTimedOut e -> {
                if (e.providerReference() != null) providerReference = e.providerReference();
                timedOut(INIT, e.reason());
            }
            case PaymentConfirmTimedOut e -> timedOut(CONFIRM, e.reason());
            case PaymentRefundTimedOut e -> timedOut(REFUND, e.reason());
            case PaymentInquiryTimedOut e -> {
                clearRequest();
                lastError = e.reason();
            }
            case PaymentInquiryResolved e -> {
                clearRequest();
                lastError = e.detail();
            }
            case PaymentRetryScheduled e -> {
                clearRequest();
                retryCount = e.retryCount();
                nextRetryAt = e.nextRetryAt();
                lastError = e.reason();
            }
            case PaymentMarkedStuck e -> {
                clearRequest();
                status = STUCK;
                lastError = e.reason();
            }
        }
        updatedAt = event.occurredAt();
        version++;
    }

    private void requested(String id, PaymentOperation operation) {
        requestId = id;
        requestedOperation = operation;
        dispatched = false;
        nextRetryAt = null;
    }

    private void clearRequest() {
        requestId = null;
        requestedOperation = null;
        dispatched = false;
        nextRetryAt = null;
    }

    private void completed(PaymentStatus completedStatus) {
        clearRequest();
        status = completedStatus;
        targetOperation = null;
        retryCount = 0;
        lastError = null;
    }

    private void timedOut(PaymentOperation target, String reason) {
        clearRequest();
        status = INQUIRY_PENDING;
        targetOperation = target;
        lastError = reason;
    }
}
