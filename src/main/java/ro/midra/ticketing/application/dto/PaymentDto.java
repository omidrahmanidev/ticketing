package ro.midra.ticketing.application.dto;

import ro.midra.ticketing.domain.PaymentStatus;

import java.math.BigDecimal;

public class PaymentDto {

    public record ConfirmPaymentRequest(Long reservationId, Long userId, String providerReference) {
    }

    public record PaymentResponse(Long paymentId, PaymentStatus status, BigDecimal amount) {
    }

    private PaymentDto() {
    }
}
