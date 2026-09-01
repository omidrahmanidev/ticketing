package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.Event;
import ro.midra.ticketing.domain.repository.EventRepository;

public interface EventJpaRepository extends JpaRepository<Event, Long>, EventRepository {
}
