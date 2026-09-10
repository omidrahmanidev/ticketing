package ro.midra.ticketing.payment.infrastructure.projection;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import ro.midra.ticketing.payment.domain.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_status_retry", columnList = "status,next_retry_at"),
        @Index(name = "idx_payments_reservation", columnList = "reservation_id")})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentViewEntity {
    @Id @Column(name = "payment_id")
    private Long paymentId;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentStatus status;
    @Column(name = "operation_id", nullable = false, unique = true)
    private String operationId;

    private String providerReference;
    @Enumerated(EnumType.STRING) @Column(name = "target_operation", length = 30)
    private PaymentOperation targetOperation;

    private int retryCount;

    private LocalDateTime nextRetryAt;
    @Column(name = "last_error", length = 500)
    private String lastError;

    private long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String requestId;
    @Enumerated(EnumType.STRING) @Column(name = "requested_operation", length = 30)
    private PaymentOperation requestedOperation;

    private boolean dispatched;
    static PaymentViewEntity from(PaymentState state) {
        PaymentViewEntity entity = new PaymentViewEntity();
        entity.paymentId = state.paymentId();
        entity.reservationId = state.reservationId();
        entity.userId = state.userId();
        entity.amount = state.amount();
        entity.status = state.status();
        entity.operationId = state.operationId();
        entity.providerReference = state.providerReference();
        entity.targetOperation = state.targetOperation();
        entity.retryCount = state.retryCount();
        entity.nextRetryAt = state.nextRetryAt();
        entity.lastError = state.lastError();
        entity.version = state.version();
        entity.createdAt = state.createdAt();
        entity.updatedAt = state.updatedAt();
        entity.requestId = state.requestId();
        entity.requestedOperation = state.requestedOperation();
        entity.dispatched = state.dispatched();
        return entity;
    }
    PaymentState state() {
        return new PaymentState(paymentId, reservationId, userId, amount, status, operationId, providerReference, targetOperation, retryCount, nextRetryAt, lastError, version, createdAt, updatedAt, requestId, requestedOperation, dispatched);
    }
}
