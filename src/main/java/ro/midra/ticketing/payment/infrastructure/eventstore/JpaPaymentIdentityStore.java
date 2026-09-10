package ro.midra.ticketing.payment.infrastructure.eventstore;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.payment.application.port.PaymentIdentityStore;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaPaymentIdentityStore implements PaymentIdentityStore {
    private final PaymentIdentityJpaRepository repository;
    @Transactional(propagation = Propagation.MANDATORY)
    public Long allocate(Long reservationId) {
        return repository.saveAndFlush(new PaymentIdentityEntity(reservationId)).getPaymentId();
    }
    public Optional<Long> findLatestPaymentId(Long reservationId) {
        return repository.findTopByReservationIdOrderByPaymentIdDesc(reservationId).map(PaymentIdentityEntity::getPaymentId);
    }
    public List<Long> findAllPaymentIds(Long reservationId) {
        return repository.findByReservationIdOrderByPaymentIdAsc(reservationId).stream()
                .map(PaymentIdentityEntity::getPaymentId).toList();
    }
}
