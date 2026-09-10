package ro.midra.ticketing.domain;

public enum SeatStatus {
    /** The seat can be reserved. */
    AVAILABLE,

    /** The seat is temporarily reserved pending payment. */
    HELD,

    /** The seat has been purchased. */
    PAID
}
