package ro.midra.ticketing.payment.infrastructure.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentViewJpaRepository extends JpaRepository<PaymentViewEntity, Long> {
    @Query("select p.paymentId from PaymentViewEntity p where p.nextRetryAt <= :now order by p.nextRetryAt, p.paymentId")
    List<Long> due(LocalDateTime now, Pageable page);
}
