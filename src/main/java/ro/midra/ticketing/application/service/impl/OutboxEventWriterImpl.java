package ro.midra.ticketing.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventWriterImpl implements OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void write(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .published(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Written in the same DB transaction as the caller (e.g. seat hold, payment).
            // Either both the business change and this row commit, or neither does.
            outboxEventRepository.save(event);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox event payload", ex);
        }
    }
}
