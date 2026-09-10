package ro.midra.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_events_payment_sequence", columnNames = {"payment_id", "sequence_number"}),
        indexes = @Index(name = "idx_payment_events_payment", columnList = "payment_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private PaymentEventType eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
