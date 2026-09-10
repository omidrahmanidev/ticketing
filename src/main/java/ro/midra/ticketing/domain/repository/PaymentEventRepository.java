package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.PaymentEvent;

import java.util.List;

public interface PaymentEventRepository {

    List<PaymentEvent> findByPaymentIdOrderBySequenceNumberAsc(Long paymentId);

    int countByPaymentId(Long paymentId);

    PaymentEvent save(PaymentEvent event);
}
