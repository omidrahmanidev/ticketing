package ro.midra.ticketing.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private static final String PURCHASE_REQUEST_TOPIC =
            "ticketing.purchase-requests";

    private final KafkaTemplate<String, PurchaseRequestMessage> kafkaTemplate;

    public void publish(PurchaseRequestMessage message) {

        /*
         * Same event + seat combination is used as Kafka key.
         *
         * Requests for the same seat go to the same partition,
         * which gives us ordering per seat.
         */
        String key = message.eventId()
                + ":"
                + message.seatIds().stream()
                .sorted()
                .findFirst()
                .orElseThrow();

        kafkaTemplate.send(
                PURCHASE_REQUEST_TOPIC,
                key,
                message
        );
    }
}