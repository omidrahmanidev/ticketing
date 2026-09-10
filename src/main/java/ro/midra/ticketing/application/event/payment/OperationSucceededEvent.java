package ro.midra.ticketing.application.event.payment;

public record OperationSucceededEvent(Operation operation, String providerReference, String detail) {
}
