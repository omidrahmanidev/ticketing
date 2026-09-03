package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.domain.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Publishes notification-style events (SEAT_HELD, SEAT_CONFIRMED, RESERVATION_EXPIRED) that
 * were written to the outbox in the same transaction as a business change.
 *
 * Note: HOLD_SEAT_COMMAND (the Redis-down queueing path) does NOT go through here -- it is
 * published directly to Kafka by KafkaSeatHoldCommandPublisher, on purpose, so that path never
 * needs a database write at all. See SeatHoldServiceImpl.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private static final String SEAT_EVENTS_TOPIC = "seat-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 500)
    @SchedulerLock(name = "publishOutboxEvents", lockAtMostFor = "PT20S", lockAtLeastFor = "PT200MS")
    @Transactional
    public void publish() {
        List<OutboxEvent> batch = outboxEventRepository.findTop100ByPublishedFalseOrderByIdAsc();
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(SEAT_EVENTS_TOPIC, event.getAggregateId(), event.getPayload());
            record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record);
            event.setPublished(true);
        }

        outboxEventRepository.saveAll(batch);
    }
}
