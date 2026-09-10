package ro.midra.ticketing.domain;

public enum PurchaseRequestStatus {
    /** The request is still being processed. */
    PROCESSING,

    /** The request completed successfully. */
    SUCCEEDED,

    /** The request could not be completed. */
    FAILED
}
