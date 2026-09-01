package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.Event;
import ro.midra.ticketing.domain.EventStatus;

import java.util.List;
import java.util.Optional;

public interface EventRepository {

    Optional<Event> findById(Long eventId);

    List<Event> findByStatus(EventStatus status);

    Event save(Event event);
}
