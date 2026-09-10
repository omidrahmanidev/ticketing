package ro.midra.ticketing.application.event.payment;

public record InquiryPerformedEvent(Operation targetOperation, boolean resolvedAsSucceeded, String detail) {
}
