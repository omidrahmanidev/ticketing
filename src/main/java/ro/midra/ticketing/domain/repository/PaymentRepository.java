package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.Payment;
import ro.midra.ticketing.domain.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByReservationReservationId(Long reservationId);

    List<Payment> findAllByReservationReservationId(Long reservationId);

    List<Payment> findTop50ByStatusInAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            List<PaymentStatus> statuses, LocalDateTime before);

    List<Payment> findTop50ByStatusInAndNextRetryAtIsNullOrderByPaymentIdAsc(List<PaymentStatus> statuses);

    Payment save(Payment payment);
}
