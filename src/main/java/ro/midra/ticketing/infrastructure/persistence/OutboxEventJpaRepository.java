package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.domain.repository.OutboxEventRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long>, OutboxEventRepository {
}
