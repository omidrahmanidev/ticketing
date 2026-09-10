package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository {

    List<OutboxEvent> findDue(java.time.LocalDateTime now, org.springframework.data.domain.Pageable page);

    java.util.Optional<OutboxEvent> findAndLockById(Long id);

    OutboxEvent save(OutboxEvent outboxEvent);

}
