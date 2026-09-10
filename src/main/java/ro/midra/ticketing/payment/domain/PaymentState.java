package ro.midra.ticketing.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentState(Long paymentId, Long reservationId, Long userId, BigDecimal amount,
                           PaymentStatus status, String operationId, String providerReference,
                           PaymentOperation targetOperation, int retryCount, LocalDateTime nextRetryAt,
                           String lastError, long version, LocalDateTime createdAt, LocalDateTime updatedAt,
                           String requestId, PaymentOperation requestedOperation, boolean dispatched) {}
