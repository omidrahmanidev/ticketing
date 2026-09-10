package ro.midra.ticketing.application.event.payment;

public enum Operation {
    /** Initializes a payment with the payment provider. */
    INIT,

    /** Confirms an initialized payment with the payment provider. */
    CONFIRM,

    /** Returns a previously processed payment to the payer. */
    REFUND
}
