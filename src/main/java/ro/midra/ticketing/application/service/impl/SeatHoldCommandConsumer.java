package ro.midra.ticketing.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;
import ro.midra.ticketing.application.service.SeatHoldService;
import ro.midra.ticketing.domain.ProcessedEvent;
import ro.midra.ticketing.domain.repository.ProcessedEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Drains seat-hold-commands (published directly by KafkaSeatHoldCommandPublisher when Redis
 * was down) at a controlled concurrency, so MySQL only ever sees a rate it can sustain.
 *
 * Note: @Transactional works here because this method is invoked by the Kafka listener
 * container (an external caller going through the Spring proxy) -- unlike a private method
 * called from within the same class, this is not self-invocation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldCommandConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final SeatHoldService seatHoldService;
    private final ObjectMapper objectMapper;

    // "concurrency" caps how many threads pull from this topic at once -- this is the knob
    // that keeps MySQL from being hit by all 500k requests at the same time. Tune it to
    // whatever the MySQL connection pool can actually sustain.
    @KafkaListener(topics = "seat-hold-commands", groupId = "ticketing-seat-hold-workers", concurrency = "5")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        String eventId = headerValue(record, "eventId");
        if (eventId == null) {
            log.warn("Received seat-hold-commands message without eventId header, skipping: {}", record.value());
            return;
        }

        // Kafka is at-least-once: guard against processing the same delivery twice. Business
        // idempotency (same requestId submitted twice by the client) is handled separately,
        // inside SeatHoldTransactionalOps, via the PurchaseRequest table.
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate hold command delivery, eventId={} already processed, skipping", eventId);
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));

        HoldSeatsCommandPayload command = objectMapper.readValue(record.value(), HoldSeatsCommandPayload.class);
        seatHoldService.processQueuedHold(command);
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
