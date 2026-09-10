package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.payment.application.port.PaymentEventStore;
import ro.midra.ticketing.payment.application.port.PaymentIdentityStore;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentHistoryQueryService {
    private final PaymentIdentityStore identities;
    private final PaymentEventStore events;

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(Long reservationId) {
        return identities.findAllPaymentIds(reservationId).stream()
                .map(id -> PaymentQueryService.response(events.load(id).aggregate().state())).toList();
    }
}
