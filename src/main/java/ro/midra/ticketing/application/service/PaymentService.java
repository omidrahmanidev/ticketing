package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;

public interface PaymentService {

    PaymentResponse confirmPayment(ConfirmPaymentRequest request);
}
