package ro.midra.ticketing.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;
import ro.midra.ticketing.application.service.SeatHoldCommandPublisher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaSeatHoldCommandPublisher implements SeatHoldCommandPublisher {

    private static final String TOPIC = "seat-hold-commands";
    private static final String EVENT_TYPE = "HOLD_SEAT_COMMAND";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(HoldSeatsCommandPayload command) {
        try {
            String json = objectMapper.writeValueAsString(command);

            // eventId here is a *delivery* id (for the consumer's duplicate-delivery check),
            // separate from command.requestId(), which is the *business* idempotency key.
            String eventId = UUID.randomUUID().toString();

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(TOPIC, command.requestId(), json);
            record.headers().add("eventId", eventId.getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", EVENT_TYPE.getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record);

        } catch (Exception ex) {
            // No outbox safety net here on purpose -- there was never a DB write to piggyback
            // on. If Kafka itself can't be reached, surface it to the caller so the client
            // knows to retry, instead of silently dropping the purchase attempt.
            throw new IllegalStateException("Failed to publish seat-hold command to Kafka", ex);
        }
    }
}
