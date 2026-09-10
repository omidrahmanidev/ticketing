package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.projection.PaymentProjectionState;
import ro.midra.ticketing.domain.PaymentEventType;

public interface PaymentEventStore {

    void append(Long paymentId, PaymentEventType type, Object payload);

    PaymentProjectionState replay(Long paymentId);
}
