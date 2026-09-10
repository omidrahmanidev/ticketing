package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.payment.application.port.*;
import ro.midra.ticketing.payment.domain.PaymentAggregate;

@Component
@RequiredArgsConstructor
public class PaymentCommitter {
    private final PaymentEventStore events;
    private final PaymentProjection projection;
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(PaymentAggregate payment) {
        if (payment.getUncommittedEvents().isEmpty()) return;
        events.append(payment.state().paymentId(), payment.committedVersion(), payment.getUncommittedEvents());
        projection.project(events.load(payment.state().paymentId()));
        payment.markEventsCommitted();
    }
}
