package ro.midra.ticketing.domain;

public enum PaymentOperation {
    /** Initializes a payment with the payment provider. */
    INIT,

    /** Confirms an initialized payment with the payment provider. */
    CONFIRM,

    /** Returns a previously processed payment to the payer. */
    REFUND
}
