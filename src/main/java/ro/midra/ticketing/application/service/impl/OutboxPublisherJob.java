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

@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private static final String SEAT_EVENTS_TOPIC = "seat-events";
    private static final String SEAT_HOLD_COMMANDS_TOPIC = "seat-hold-commands";
    private static final String HOLD_SEAT_COMMAND_TYPE = "HOLD_SEAT_COMMAND";

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
            // HOLD_SEAT_COMMAND rows are work items for the seat-hold worker pool (used when
            // Redis was down at request time); everything else is a notification-style event
            // for downstream consumers (email, analytics, finance, ...).
            String topic = HOLD_SEAT_COMMAND_TYPE.equals(event.getEventType())
                    ? SEAT_HOLD_COMMANDS_TOPIC
                    : SEAT_EVENTS_TOPIC;

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, event.getAggregateId(), event.getPayload());
            record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record);
            event.setPublished(true);
        }

        outboxEventRepository.saveAll(batch);
    }
}
