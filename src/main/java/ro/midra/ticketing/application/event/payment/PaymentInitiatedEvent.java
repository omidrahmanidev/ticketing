package ro.midra.ticketing.application.event.payment;

import java.math.BigDecimal;

public record PaymentInitiatedEvent(Long reservationId, Long userId, BigDecimal amount, String operationId) {
}
