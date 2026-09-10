package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.payment.infrastructure.outbox.PaymentOutboxRouter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * At-least-once delivery. Local payment work uses the same outbox as Kafka notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherJob {
    private final OutboxDeliveryTransactions delivery;
    private final PaymentOutboxRouter paymentRouter;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 500)
    @SchedulerLock(name = "publishOutboxEvents", lockAtMostFor = "PT2M", lockAtLeastFor = "PT200MS")
    @Transactional(propagation = Propagation.NEVER)
    public void publish() {
        for (var event : delivery.batch()) {
            try {
                if (!paymentRouter.deliver(event)) {
                    var record = new ProducerRecord<String, String>("seat-events", event.getAggregateId(), event.getPayload());
                    record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
                    record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                    kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
                }
                delivery.acknowledge(event.getId());
            } catch (Exception ex) {
                log.warn("Outbox delivery failed eventId={} type={}", event.getEventId(), event.getEventType(), ex);
                delivery.retryLater(event.getId());
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
