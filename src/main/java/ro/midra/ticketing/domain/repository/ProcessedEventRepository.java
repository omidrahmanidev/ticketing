package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.ProcessedEvent;

public interface ProcessedEventRepository {

    boolean existsById(String eventId);

    ProcessedEvent save(ProcessedEvent processedEvent);
}
