package ro.midra.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_published", columnList = "published")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "available_at")
    private LocalDateTime availableAt;

    @Column(name = "delivery_attempts")
    private Integer deliveryAttempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
