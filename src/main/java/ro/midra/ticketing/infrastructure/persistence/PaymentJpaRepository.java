package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.Payment;
import ro.midra.ticketing.domain.repository.PaymentRepository;

import java.util.List;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long>, PaymentRepository {

    @Override
    List<Payment> findByReservationReservationId(Long reservationId);
}
