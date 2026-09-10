package ro.midra.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a payment for a reservation and its progress through provider processing.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_reservation", columnList = "reservation_id"),
                @Index(name = "idx_payments_provider_reference",
                        columnList = "provider_reference"),
                @Index(name = "idx_payments_status_retry", columnList = "status,next_retry_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    /** Unique identifier of the payment. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    /** Version used for optimistic locking. */
    @Version
    private Long version;

    /** Reservation for which this payment is being processed. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    /** User who initiated the payment. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Monetary amount to be charged. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Current stage of the payment lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    /** Unique idempotency key for the payment operation. */
    @Column(name = "operation_id", nullable = false, unique = true, length = 255)
    private String operationId;

    /** Provider operation that should be retried or reconciled. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_operation", length = 30)
    private PaymentOperation targetOperation;

    /** Number of provider-operation attempts made so far. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Time at which the next retry may be attempted. */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Most recent processing error, when one occurred. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Identifier assigned to the payment by the provider. */
    @Column(name = "provider_reference")
    private String providerReference;

    /** Timestamp at which the payment record was created. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Timestamp at which the payment record was last updated. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
