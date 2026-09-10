package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;

@Service
@RequiredArgsConstructor
public class StartPaymentHandler {
    private final StartPaymentTransaction transaction;

    public PaymentResponse startPayment(ConfirmPaymentRequest request) {
        try {
            return PaymentQueryService.response(transaction.start(request));
        } catch (DataIntegrityViolationException race) {
            // Retry an integrity failure only after the transaction has rolled back.
            // The second transaction revalidates ownership and the latest event stream under the reservation lock.
            try {
                return PaymentQueryService.response(transaction.start(request));
            } catch (DataIntegrityViolationException again) {
                throw race;
            }
        }
    }
}
