package ro.midra.ticketing.domain;

public enum ReservationStatus {
    /** Seats are temporarily reserved pending payment. */
    HELD,

    /** Payment has been started for the reservation. */
    PAYMENT_PENDING,

    /** Payment was completed and the reservation is final. */
    PAID,

    /** The reservation hold elapsed before payment completed. */
    EXPIRED,

    /** The reservation was cancelled. */
    CANCELLED
}
