package ro.midra.ticketing.domain;

public enum EventStatus {
    /** The event is being prepared and is not visible for sale. */
    DRAFT,

    /** Tickets for the event are available for purchase. */
    ON_SALE,

    /** No tickets remain available for purchase. */
    SOLD_OUT,

    /** The event has concluded. */
    FINISHED,

    /** The event will not take place. */
    CANCELLED
}
