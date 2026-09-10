package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.midra.ticketing.domain.Payment;
import ro.midra.ticketing.domain.PaymentStatus;
import ro.midra.ticketing.domain.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    @Override
    @Query("select p from Payment p where p.reservation.reservationId = :reservationId " +
            "and p.status not in (ro.midra.ticketing.domain.PaymentStatus.INIT_FAILED, " +
            "ro.midra.ticketing.domain.PaymentStatus.REFUNDED)")
    Optional<Payment> findByReservationReservationId(@Param("reservationId") Long reservationId);

    @Override
    List<Payment> findAllByReservationReservationId(Long reservationId);

    @Override
    List<Payment> findTop50ByStatusInAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            List<PaymentStatus> statuses, LocalDateTime before);

    @Override
    List<Payment> findTop50ByStatusInAndNextRetryAtIsNullOrderByPaymentIdAsc(List<PaymentStatus> statuses);
}
