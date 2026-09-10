package ro.midra.ticketing.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.service.SeatReadModelPort;
import ro.midra.ticketing.domain.ProcessedEvent;
import ro.midra.ticketing.domain.ProcessedEventId;
import ro.midra.ticketing.domain.SeatStatus;
import ro.midra.ticketing.domain.repository.ProcessedEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReadModelProjector {

    private static final String CONSUMER_GROUP = "ticketing-seat-read-model";

    private final ProcessedEventRepository processedEventRepository;
    private final SeatReadModelPort seatReadModelPort;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "seat-events", groupId = CONSUMER_GROUP, concurrency = "3")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        String deliveryEventId = headerValue(record, "eventId");
        if (deliveryEventId == null) {
            log.warn("Received seat-events message without eventId header, skipping: {}", record.value());
            return;
        }

        if (processedEventRepository.existsByIdEventIdAndIdConsumerGroup(deliveryEventId, CONSUMER_GROUP)) {
            log.info("Duplicate seat read-model message, eventId={} already processed, skipping", deliveryEventId);
            return;
        }

        processedEventRepository.save(new ProcessedEvent(
                new ProcessedEventId(deliveryEventId, CONSUMER_GROUP), LocalDateTime.now()));

        String eventType = headerValue(record, "eventType");
        SeatStatus status = targetStatus(eventType);
        if (status == null) {
            log.warn("Unknown seat event type {}, skipping", eventType);
            return;
        }

        SeatEventPayload payload = objectMapper.readValue(record.value(), SeatEventPayload.class);
        payload.seatIds().forEach(seatId -> seatReadModelPort.updateStatus(payload.eventId(), seatId, status));
    }

    private SeatStatus targetStatus(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "SEAT_HELD" -> SeatStatus.HELD;
            case "SEAT_CONFIRMED" -> SeatStatus.PAID;
            case "RESERVATION_EXPIRED", "RESERVATION_CANCELLED" -> SeatStatus.AVAILABLE;
            default -> null;
        };
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
