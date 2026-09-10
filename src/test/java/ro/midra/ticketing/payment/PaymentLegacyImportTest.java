package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import ro.midra.ticketing.payment.domain.*;
import ro.midra.ticketing.payment.infrastructure.eventstore.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PaymentLegacyImportTest {
    @Test void mysqlCheckpointJsonReplaysWithoutReadingLegacyProjection() {
        var at = LocalDateTime.of(2026, 9, 10, 12, 0);
        var serializer = new PaymentEventSerializer();
        var checkpoint = serializer.deserialize(new PaymentEventEntity(43L, 2, "PaymentLegacyStateImported", """
                {"state":{"paymentId":43,"reservationId":2,"userId":1,"amount":2.00,
                "operationId":"payment-43","providerReference":"ref-43","status":"INQUIRY_PENDING",
                "targetOperation":"CONFIRM","retryCount":2,"nextRetryAt":"2026-09-10T12:00:00.000000",
                "lastError":"unknown","version":2,"createdAt":"2026-09-10T11:00:00.000000",
                "updatedAt":"2026-09-10T12:00:00.000000","requestId":null,"requestedOperation":null,"dispatched":false},
                "occurredAt":"2026-09-10T12:00:00.000000"}
                """, at));
        var payment = PaymentAggregate.rehydrate(List.of(new PaymentEvent.PaymentInitiated(43L, 2L, 1L,
                new BigDecimal("2.00"), "payment-43", at.minusHours(1)), checkpoint));
        assertThat(payment.state().status()).isEqualTo(PaymentStatus.INQUIRY_PENDING);
        assertThat(payment.state().providerReference()).isEqualTo("ref-43");
        assertThat(payment.state().targetOperation()).isEqualTo(PaymentOperation.CONFIRM);
        assertThat(payment.state().nextRetryAt()).isEqualTo(at);
        assertThat(payment.state().retryCount()).isEqualTo(2);
        assertThat(payment.state().lastError()).isEqualTo("unknown");
        assertThat(payment.getUncommittedEvents()).isEmpty();
        payment.inquiryRequested("recovery", at);
        payment.inquiryResolved(true, "ref-43", "resolved", at);
        assertThat(payment.state().status()).isEqualTo(PaymentStatus.CONFIRMED);
    }
}
