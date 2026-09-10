package ro.midra.ticketing.payment.application.port;

import ro.midra.ticketing.payment.domain.PaymentEvent;
import java.util.List;

public interface PaymentEventStore {
    PaymentEventStream load(Long paymentId);
    void append(Long paymentId, long expectedVersion, List<PaymentEvent> events);
    List<Long> paymentIdsAfter(long afterId, int limit);
}
