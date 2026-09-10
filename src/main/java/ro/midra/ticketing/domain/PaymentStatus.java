package ro.midra.ticketing.domain;

/**
 * Represents the current stage of a payment in its processing lifecycle.
 */
public enum PaymentStatus {
    /** The payment has been created and its initialization must be sent to the payment provider. */
    INIT_PENDING,

    /** Payment initialization failed definitively and no further processing is attempted. */
    INIT_FAILED,

    /** Initialization succeeded; the payment now awaits confirmation from the provider. */
    AWAITING_CONFIRM,

    /** A provider operation timed out, so its final outcome must be determined through an inquiry. */
    INQUIRY_PENDING,

    /** The provider confirmed the payment successfully. */
    CONFIRMED,

    /** The payment must be refunded, or a previously attempted refund needs to be retried. */
    REFUND_PENDING,

    /** The provider confirmed that the payment was refunded. */
    REFUNDED,

    /** Automatic retries have been exhausted and the payment requires manual intervention. */
    STUCK
}