package ro.midra.ticketing.application.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.midra.ticketing.application.event.payment.*;
import ro.midra.ticketing.domain.PaymentEvent;
import ro.midra.ticketing.domain.PaymentEventType;
import ro.midra.ticketing.domain.PaymentOperation;
import ro.midra.ticketing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

public class PaymentProjectionState {

    private PaymentStatus status;
    private BigDecimal amount;
    private String providerReference;
    private PaymentOperation targetOperation;
    private int retryCount;
    private String lastError;

    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getProviderReference() { return providerReference; }
    public PaymentOperation getTargetOperation() { return targetOperation; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }

    public static PaymentProjectionState fold(List<PaymentEvent> eventsInOrder) {
        PaymentProjectionState state = new PaymentProjectionState();
        ObjectMapper mapper = new ObjectMapper();
        for (PaymentEvent event : eventsInOrder) {
            try {
                // This deliberately mirrors the orchestrator transitions: replay independently
                // proves that the append-only history, rather than the JPA projection, is truth.
                switch (event.getEventType()) {
                    case PAYMENT_INITIATED -> {
                        PaymentInitiatedEvent payload = mapper.readValue(event.getPayload(), PaymentInitiatedEvent.class);
                        state.amount = payload.amount();
                        state.status = PaymentStatus.INIT_PENDING;
                    }
                    case OPERATION_SUCCEEDED -> {
                        OperationSucceededEvent payload = mapper.readValue(event.getPayload(), OperationSucceededEvent.class);
                        state.providerReference = payload.providerReference();
                        if (payload.operation() == Operation.INIT) { state.status = PaymentStatus.AWAITING_CONFIRM; state.retryCount = 0; state.lastError = null; }
                    }
                    case OPERATION_FAILED -> {
                        OperationFailedEvent payload = mapper.readValue(event.getPayload(), OperationFailedEvent.class);
                        state.lastError = payload.reason() + ": " + payload.detail();
                        state.status = switch (payload.operation()) {
                            case INIT -> PaymentStatus.INIT_FAILED;
                            case CONFIRM -> PaymentStatus.REFUND_PENDING;
                            case REFUND -> {
                                state.retryCount++;
                                yield PaymentStatus.REFUND_PENDING;
                            }
                        };
                    }
                    case OPERATION_TIMED_OUT -> {
                        OperationTimedOutEvent payload = mapper.readValue(event.getPayload(), OperationTimedOutEvent.class);
                        state.status = PaymentStatus.INQUIRY_PENDING;
                        state.targetOperation = PaymentOperation.valueOf(payload.operation().name());
                        state.lastError = payload.detail();
                    }
                    case INQUIRY_PERFORMED -> {
                        InquiryPerformedEvent payload = mapper.readValue(event.getPayload(), InquiryPerformedEvent.class);
                        if ("TIMEOUT".equals(payload.detail()) || "UNKNOWN".equals(payload.detail())) {
                            state.retryCount++;
                            state.lastError = payload.detail();
                        } else if (payload.resolvedAsSucceeded()) {
                            state.targetOperation = null;
                            state.retryCount = 0;
                            state.lastError = null;
                            state.status = switch (payload.targetOperation()) {
                                case INIT -> PaymentStatus.AWAITING_CONFIRM;
                                case CONFIRM -> PaymentStatus.CONFIRMED;
                                case REFUND -> PaymentStatus.REFUNDED;
                            };
                        } else {
                            state.targetOperation = null;
                            state.status = payload.targetOperation() == Operation.INIT
                                    ? PaymentStatus.INIT_FAILED : PaymentStatus.REFUND_PENDING;
                        }
                    }
                    case PAYMENT_CONFIRMED -> { state.status = PaymentStatus.CONFIRMED; state.targetOperation = null; state.retryCount = 0; state.lastError = null; }
                    case PAYMENT_REFUNDED -> { state.status = PaymentStatus.REFUNDED; state.targetOperation = null; state.retryCount = 0; state.lastError = null; }
                    case PAYMENT_MARKED_STUCK -> {
                        PaymentMarkedStuckEvent payload = mapper.readValue(event.getPayload(), PaymentMarkedStuckEvent.class);
                        state.status = PaymentStatus.STUCK;
                        state.lastError = payload.reason();
                    }
                    case OPERATION_ATTEMPTED -> { }
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Could not replay payment event " + event.getId(), ex);
            }
        }
        return state;
    }
}
