package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.ProcessedEvent;
import ro.midra.ticketing.domain.ProcessedEventId;
import ro.midra.ticketing.domain.repository.ProcessedEventRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEvent, ProcessedEventId>, ProcessedEventRepository {
}
