package ro.midra.ticketing.application.service;

import java.math.BigDecimal;

public interface PaymentGateway {

    GatewayResult init(String idempotencyKey, BigDecimal amount, String scenarioHint);

    GatewayResult confirm(String idempotencyKey, String providerReference);

    GatewayResult refund(String idempotencyKey, String providerReference, BigDecimal amount);

    GatewayResult inquiry(String idempotencyKey, String providerReference);

    record GatewayResult(Outcome outcome, String providerReference, String detail) {
    }

    enum Outcome {
        /** The gateway completed the requested operation successfully. */
        SUCCESS,

        /** The payer does not have sufficient funds to complete the operation. */
        INSUFFICIENT_FUNDS,

        /** A transient gateway failure occurred and the operation may be retried. */
        TEMPORARY_FAILURE,

        /** The gateway did not return a result before the operation timed out. */
        TIMEOUT,

        /** The gateway rejected a payment confirmation. */
        CONFIRMATION_FAILED,

        /** The gateway completed the refund successfully. */
        REFUND_SUCCESS,

        /** The gateway could not complete the refund. */
        REFUND_FAILURE,

        /** The gateway could not determine the operation's outcome. */
        UNKNOWN
    }
}
