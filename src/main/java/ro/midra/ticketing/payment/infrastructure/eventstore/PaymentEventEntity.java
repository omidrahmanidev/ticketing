package ro.midra.ticketing.payment.infrastructure.eventstore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_events_payment_sequence", columnNames = {"payment_id", "sequence_number"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;
    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Lob @Column(nullable = false)
    private String payload;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime occurredAt;

    public PaymentEventEntity(Long paymentId, long sequence, String type, String payload, LocalDateTime at) {
        this.paymentId = paymentId; this.sequenceNumber = sequence; this.eventType = type;
        this.payload = payload; this.occurredAt = at;
    }
}
