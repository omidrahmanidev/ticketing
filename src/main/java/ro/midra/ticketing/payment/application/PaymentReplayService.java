package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.payment.application.port.*;

@Service
@RequiredArgsConstructor
public class PaymentReplayService {
    private final PaymentEventStore events;
    private final PaymentProjection projection;
    public PaymentResponse replay(Long id) { return PaymentQueryService.response(events.load(id).aggregate().state()); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse rebuild(Long id) {
        var stream = events.load(id);
        projection.project(stream);
        return PaymentQueryService.response(stream.aggregate().state());
    }
}
