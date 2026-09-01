package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository {

    List<OutboxEvent> findTop100ByPublishedFalseOrderByIdAsc();

    OutboxEvent save(OutboxEvent outboxEvent);

    void saveAll(List<OutboxEvent> batch);
}
