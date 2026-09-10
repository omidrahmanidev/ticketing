package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.PaymentEvent;
import ro.midra.ticketing.domain.repository.PaymentEventRepository;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEvent, Long>, PaymentEventRepository {
}
