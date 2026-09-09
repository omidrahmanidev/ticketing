package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.domain.ProcessedEvent;
import ro.midra.ticketing.domain.ProcessedEventId;
import ro.midra.ticketing.domain.repository.ProcessedEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatEventConsumer {

    private static final String CONSUMER_GROUP = "ticketing-notification-service";

    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "seat-events", groupId = CONSUMER_GROUP)
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventId = headerValue(record, "eventId");
        if (eventId == null) {
            log.warn("Received seat-events message without eventId header, skipping: {}", record.value());
            return;
        }

        // Kafka is at-least-once: the same message can arrive more than once.
        // This check makes processing it twice have the same effect as processing it once.
        if (processedEventRepository.existsByIdEventIdAndIdConsumerGroup(eventId, CONSUMER_GROUP)) {
            log.info("Duplicate kafka message, eventId={} already processed, skipping", eventId);
            return;
        }

        processedEventRepository.save(new ProcessedEvent(
                new ProcessedEventId(eventId, CONSUMER_GROUP), LocalDateTime.now()));

        String eventType = headerValue(record, "eventType");
        log.info("Processed seat event eventId={} type={} key={} payload={}",
                eventId, eventType, record.key(), record.value());

        // downstream side effects (notifications, analytics, etc.) go here
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
