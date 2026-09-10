package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ro.midra.ticketing.application.service.impl.*;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.payment.infrastructure.outbox.PaymentOutboxRouter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutboxPublisherJobTest {
    private final OutboxDeliveryTransactions delivery = mock(OutboxDeliveryTransactions.class);
    private final PaymentOutboxRouter router = mock(PaymentOutboxRouter.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxPublisherJob publisher = new OutboxPublisherJob(delivery, router, kafka);
    private OutboxEvent event() {
        return OutboxEvent.builder().id(1L).eventId("event-1").aggregateType("Reservation").aggregateId("1")
                .eventType("SEAT_CONFIRMED").payload("{}").build();
    }
    @Test void failedBrokerAcknowledgementLeavesMessageForRetry() {
        when(delivery.batch()).thenReturn(List.of(event()));
        when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        publisher.publish();
        verify(delivery, never()).acknowledge(anyLong());
        verify(delivery).retryLater(1L);
    }
    @Test void successfulBrokerAcknowledgementPrecedesOutboxAcknowledgement() {
        when(delivery.batch()).thenReturn(List.of(event()));
        var ack = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenAnswer(call -> {
            verify(delivery, never()).acknowledge(anyLong());
            ack.complete(null);
            return ack;
        });
        publisher.publish();
        verify(delivery).acknowledge(1L);
        verify(delivery, never()).retryLater(anyLong());
    }
    @Test void localPaymentCommandDoesNotRequireKafka() throws Exception {
        var event = event();
        when(delivery.batch()).thenReturn(List.of(event));
        when(router.deliver(event)).thenReturn(true);
        publisher.publish();
        verifyNoInteractions(kafka);
        verify(delivery).acknowledge(1L);
    }
}
