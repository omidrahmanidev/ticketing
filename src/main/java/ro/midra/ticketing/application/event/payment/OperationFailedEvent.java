package ro.midra.ticketing.application.event.payment;

public record OperationFailedEvent(Operation operation, String reason, String detail) {
}
