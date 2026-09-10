package ro.midra.ticketing.payment.application.port;

import ro.midra.ticketing.payment.domain.PaymentAggregate;
import ro.midra.ticketing.payment.domain.PaymentEvent;
import java.util.List;

public record PaymentEventStream(Long paymentId, List<PaymentEvent> events) {
    public PaymentEventStream { events = List.copyOf(events); }
    public PaymentAggregate aggregate() { return PaymentAggregate.rehydrate(events); }
}
