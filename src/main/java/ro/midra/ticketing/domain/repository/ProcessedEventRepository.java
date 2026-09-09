package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.ProcessedEvent;

public interface ProcessedEventRepository {

    boolean existsByIdEventIdAndIdConsumerGroup(String eventId, String consumerGroup);

    ProcessedEvent save(ProcessedEvent processedEvent);
}
