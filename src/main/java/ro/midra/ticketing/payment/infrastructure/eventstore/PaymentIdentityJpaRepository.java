package ro.midra.ticketing.payment.infrastructure.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentIdentityJpaRepository extends JpaRepository<PaymentIdentityEntity, Long> {
    Optional<PaymentIdentityEntity> findTopByReservationIdOrderByPaymentIdDesc(Long reservationId);
    List<PaymentIdentityEntity> findByReservationIdOrderByPaymentIdAsc(Long reservationId);
}
