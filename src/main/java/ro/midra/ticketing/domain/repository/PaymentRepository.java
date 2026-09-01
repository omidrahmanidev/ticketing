package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long paymentId);

    List<Payment> findByReservationReservationId(Long reservationId);

    Payment save(Payment payment);
}
