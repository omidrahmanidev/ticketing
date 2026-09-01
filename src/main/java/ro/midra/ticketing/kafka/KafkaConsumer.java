package ro.midra.ticketing.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import ro.midra.ticketing.service.PurchaseRequestService;

@Component
@RequiredArgsConstructor
public class KafkaConsumer {

    private final PurchaseRequestService purchaseRequestService;

    @KafkaListener(
            topics = "ticketing.purchase-requests",
            groupId = "ticketing-reservation-workers"
    )
    public void consume(PurchaseRequestMessage message) {

        /*
         * Kafka can deliver the same message more than once.
         *
         * PurchaseRequestService handles idempotency using requestId.
         */
        purchaseRequestService.process(message);
    }
}