package ro.midra.ticketing.application.event.payment;

public record OperationTimedOutEvent(Operation operation, String detail) {
}
