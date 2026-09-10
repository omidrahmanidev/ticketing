package ro.midra.ticketing.payment.application;

import ro.midra.ticketing.payment.domain.PaymentOperation;

/** Scenario is a demo adapter hint, never part of aggregate state. */
public record PaymentCommand(Long paymentId, String requestId, PaymentOperation operation, String scenarioHint) {
    public String messageType() {
        return switch (operation) {
            case INIT -> "INIT_PAYMENT";
            case CONFIRM -> "CONFIRM_PAYMENT";
            case INQUIRY -> "INQUIRE_PAYMENT";
            case REFUND -> "REFUND_PAYMENT";
        };
    }
}
