package ro.midra.ticketing.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Facts contain their effective time; replay never consults a clock. */
public sealed interface PaymentEvent {
    LocalDateTime occurredAt();
    record PaymentInitiated(Long paymentId, Long reservationId, Long userId, BigDecimal amount,
                            String operationId, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInitRequested(String requestId, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInitSucceeded(String providerReference, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInitFailed(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInitTimedOut(String reason, String providerReference, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentConfirmRequested(String requestId, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentConfirmed(String providerReference, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentConfirmFailed(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentConfirmTimedOut(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInquiryRequested(String requestId, PaymentOperation targetOperation,
                                   LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInquiryResolved(PaymentOperation targetOperation, boolean succeeded, String providerReference,
                                  String detail, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentInquiryTimedOut(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentRefundRequested(String requestId, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentRefunded(String providerReference, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentRefundFailed(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentRefundTimedOut(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentRetryScheduled(int retryCount, LocalDateTime nextRetryAt, String reason,
                                 LocalDateTime occurredAt) implements PaymentEvent {}
    record PaymentMarkedStuck(String reason, LocalDateTime occurredAt) implements PaymentEvent {}
    /** One-time cutover fact: the legacy audit log omitted state required for exact replay. */
    record PaymentLegacyStateImported(PaymentState state, LocalDateTime occurredAt) implements PaymentEvent {}
    /** A committed dispatch fences duplicate workers and gives recovery a deadline. */
    record PaymentProviderCallStarted(String requestId, LocalDateTime recoverAfter,
                                      LocalDateTime occurredAt) implements PaymentEvent {}
}
