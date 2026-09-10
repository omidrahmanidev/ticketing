package ro.midra.ticketing.application.event.payment;

public record OperationAttemptedEvent(Operation operation, int attemptNumber, String idempotencyKey) {
}
