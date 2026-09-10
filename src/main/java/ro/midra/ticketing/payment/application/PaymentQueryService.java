package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.payment.application.port.PaymentProjection;
import ro.midra.ticketing.payment.domain.PaymentState;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {
    private final PaymentProjection projection;
    public PaymentResponse status(Long id) {
        return response(projection.find(id).orElseThrow(() -> new NotFoundException("Payment projection not found: " + id)));
    }
    static PaymentResponse response(PaymentState state) {
        return new PaymentResponse(state.paymentId(), state.status(), state.amount(), state.providerReference(), state.retryCount(), state.lastError());
    }
}
