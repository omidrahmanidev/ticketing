package ro.midra.ticketing.domain;

public enum PaymentEventType {
    /** A payment was created and initialization was requested. */
    PAYMENT_INITIATED,

    /** An operation against the payment provider was attempted. */
    OPERATION_ATTEMPTED,

    /** An operation against the payment provider completed successfully. */
    OPERATION_SUCCEEDED,

    /** An operation against the payment provider failed. */
    OPERATION_FAILED,

    /** An operation against the payment provider timed out. */
    OPERATION_TIMED_OUT,

    /** The provider was queried to determine an operation's outcome. */
    INQUIRY_PERFORMED,

    /** The payment was confirmed by the provider. */
    PAYMENT_CONFIRMED,

    /** The payment was refunded by the provider. */
    PAYMENT_REFUNDED,

    /** The payment requires manual intervention. */
    PAYMENT_MARKED_STUCK
}
