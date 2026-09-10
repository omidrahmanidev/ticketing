package ro.midra.ticketing.payment.infrastructure.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEventEntity, Long> {
    List<PaymentEventEntity> findByPaymentIdOrderBySequenceNumberAsc(Long paymentId);
    @Query("select coalesce(max(e.sequenceNumber), 0) from PaymentEventEntity e where e.paymentId = :paymentId")
    long currentVersion(Long paymentId);
    @Query("select distinct e.paymentId from PaymentEventEntity e where e.paymentId > :afterId order by e.paymentId")
    List<Long> paymentIdsAfter(long afterId, Pageable page);
}
