package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.event.payment.*;
import ro.midra.ticketing.application.exception.ConcurrentPaymentModificationException;
import ro.midra.ticketing.application.exception.InvalidReservationStateException;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.application.service.PaymentEventStore;
import ro.midra.ticketing.application.service.PaymentGateway;
import ro.midra.ticketing.application.service.PaymentService;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.domain.repository.PaymentRepository;
import ro.midra.ticketing.domain.repository.ReservationRepository;
import ro.midra.ticketing.domain.repository.SeatRepository;
import ro.midra.ticketing.domain.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private static final int MAX_RETRIES = 6;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SeatLockPort seatLockPort;
    private final OutboxEventWriter outboxEventWriter;
    private final PaymentEventStore paymentEventStore;
    private final PaymentGateway paymentGateway;

    @Override
    @Transactional
    public PaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        Payment current = paymentRepository.findByReservationReservationId(request.reservationId()).orElse(null);
        if (current != null) return response(current);
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() ->
                        new NotFoundException("Reservation not found: " + request.reservationId()));
        if (reservation.getStatus() != ReservationStatus.HELD)
            throw new InvalidReservationStateException("Reservation is not in HELD state: " + reservation.getReservationId());
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new InvalidReservationStateException("Reservation hold already expired: " + reservation.getReservationId());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        if (!reservation.getUser().getUserId().equals(user.getUserId()))
            throw new InvalidReservationStateException("Reservation does not belong to user: " + reservation.getReservationId());
        LocalDateTime now = LocalDateTime.now();
        Payment payment = paymentRepository.save(Payment.builder().reservation(reservation).user(user)
                .amount(BigDecimal.valueOf(reservation.getSeats().size())).status(PaymentStatus.INIT_PENDING)
                .operationId("payment-" + UUID.randomUUID()).retryCount(0).createdAt(now).updatedAt(now).build());
        reservation.setStatus(ReservationStatus.PAYMENT_PENDING);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);
        paymentEventStore.append(payment.getPaymentId(), PaymentEventType.PAYMENT_INITIATED,
                new PaymentInitiatedEvent(reservation.getReservationId(), user.getUserId(), payment.getAmount(), payment.getOperationId()));
        return init(payment, request.testScenario());
    }

    @Override
    @Transactional
    public PaymentResponse processInit(Long id) {
        try {
            return init(require(id), null);
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(id, e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse processConfirm(Long id) {
        try {
            return confirmStep(require(id));
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(id, e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse processInquiry(Long id) {
        Payment payment = require(id);
        if (payment.getStatus() != PaymentStatus.INQUIRY_PENDING) return response(payment);
        try {
            PaymentOperation target = payment.getTargetOperation();
            PaymentGateway.GatewayResult result = paymentGateway.inquiry(key(payment, target), payment.getProviderReference());
            boolean success = result.outcome() == PaymentGateway.Outcome.SUCCESS;
            paymentEventStore.append(payment.getPaymentId(), PaymentEventType.INQUIRY_PERFORMED,
                    new InquiryPerformedEvent(Operation.valueOf(target.name()), success, result.outcome().name()));
            if (result.outcome() == PaymentGateway.Outcome.TIMEOUT || result.outcome() == PaymentGateway.Outcome.UNKNOWN) {
                String stuck = target == PaymentOperation.REFUND
                        ? "refund could not be completed after " + MAX_RETRIES + " attempts -- customer was charged and needs manual refund"
                        : "inquiry could not resolve target operation after " + MAX_RETRIES + " attempts";
                retryOrStuck(payment, result.outcome().name(), stuck);
                return response(payment);
            }
            if (target == PaymentOperation.INIT && success) {
                payment.setStatus(PaymentStatus.AWAITING_CONFIRM);
                payment.setProviderReference(result.providerReference());
                clearRetry(payment);
                payment.setLastError(null);
                paymentRepository.save(payment);
                return confirmStep(payment);
            }
            if (target == PaymentOperation.INIT) {
                failInit(payment, result.outcome(), result.detail());
                return response(payment);
            }
            if (target == PaymentOperation.REFUND && success) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.PAYMENT_REFUNDED, new PaymentRefundedEvent());
                payment.setStatus(PaymentStatus.REFUNDED);
                clearRetry(payment);
                payment.setLastError(null);
                paymentRepository.save(payment);
                release(payment);
                return response(payment);
            }
            if (success) {
                confirmed(payment);
                return response(payment);
            }
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            payment.setTargetOperation(null);
            payment.setLastError(result.outcome().name());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return refund(payment);
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(id, e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse processRefund(Long id) {
        try {
            return refund(require(id));
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(id, e);
        }
    }

    private PaymentResponse init(Payment payment, String scenario) {
        if (payment.getStatus() != PaymentStatus.INIT_PENDING) return response(payment);
        try {
            attempted(payment, PaymentOperation.INIT);
            PaymentGateway.GatewayResult gatewayResult = paymentGateway.init(key(payment, PaymentOperation.INIT), payment.getAmount(), scenario);
            if (gatewayResult.outcome() == PaymentGateway.Outcome.SUCCESS) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_SUCCEEDED, new OperationSucceededEvent(Operation.INIT, gatewayResult.providerReference(), gatewayResult.detail()));
                payment.setStatus(PaymentStatus.AWAITING_CONFIRM);
                payment.setProviderReference(gatewayResult.providerReference());
                clearRetry(payment);
                payment.setLastError(null);
                paymentRepository.save(payment);
                return confirmStep(payment);
            }
            if (gatewayResult.outcome() == PaymentGateway.Outcome.TIMEOUT) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_TIMED_OUT, new OperationTimedOutEvent(Operation.INIT, gatewayResult.detail()));
                awaitInquiry(payment, PaymentOperation.INIT, gatewayResult.detail());
                return response(payment);
            }
            failInit(payment, gatewayResult.outcome(), gatewayResult.detail());
            return response(payment);
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(payment.getPaymentId(), e);
        }
    }

    private PaymentResponse confirmStep(Payment payment) {
        if (payment.getStatus() != PaymentStatus.AWAITING_CONFIRM) return response(payment);
        try {
            attempted(payment, PaymentOperation.CONFIRM);
            PaymentGateway.GatewayResult gatewayResult = paymentGateway.confirm(key(payment, PaymentOperation.CONFIRM), payment.getProviderReference());
            if (gatewayResult.outcome() == PaymentGateway.Outcome.SUCCESS) {
                confirmed(payment);
                return response(payment);
            }
            if (gatewayResult.outcome() == PaymentGateway.Outcome.TIMEOUT) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_TIMED_OUT, new OperationTimedOutEvent(Operation.CONFIRM, gatewayResult.detail()));
                awaitInquiry(payment, PaymentOperation.CONFIRM, gatewayResult.detail());
                return response(payment);
            }
            paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_FAILED, new OperationFailedEvent(Operation.CONFIRM, gatewayResult.outcome().name(), gatewayResult.detail()));
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            payment.setLastError(gatewayResult.outcome() + ": " + gatewayResult.detail());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return refund(payment);
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(payment.getPaymentId(), e);
        }
    }

    private PaymentResponse refund(Payment payment) {
        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) return response(payment);
        try {
            attempted(payment, PaymentOperation.REFUND);
            PaymentGateway.GatewayResult result = paymentGateway.refund(key(payment, PaymentOperation.REFUND), payment.getProviderReference(), payment.getAmount());
            if (result.outcome() == PaymentGateway.Outcome.REFUND_SUCCESS) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.PAYMENT_REFUNDED, new PaymentRefundedEvent());
                payment.setStatus(PaymentStatus.REFUNDED);
                clearRetry(payment);
                payment.setLastError(null);
                paymentRepository.save(payment);
                release(payment);
                return response(payment);
            }
            if (result.outcome() == PaymentGateway.Outcome.TIMEOUT) {
                paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_TIMED_OUT,
                        new OperationTimedOutEvent(Operation.REFUND, result.detail()));
                awaitInquiry(payment, PaymentOperation.REFUND, result.detail());
                return response(payment);
            }
            paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_FAILED,
                    new OperationFailedEvent(Operation.REFUND, result.outcome().name(), result.detail()));
            retryOrStuck(payment, result.outcome() + ": " + result.detail(), "refund could not be completed after " + MAX_RETRIES + " attempts -- customer was charged and needs manual refund");
            return response(payment);
        } catch (ConcurrentPaymentModificationException e) {
            return concurrent(payment.getPaymentId(), e);
        }
    }

    private void attempted(Payment payment, PaymentOperation operation) {
        paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_ATTEMPTED, new OperationAttemptedEvent(Operation.valueOf(operation.name()), payment.getRetryCount() + 1, key(payment, operation)));
    }

    private void awaitInquiry(Payment payment, PaymentOperation paymentOperation, String detail) {
        payment.setStatus(PaymentStatus.INQUIRY_PENDING);
        payment.setTargetOperation(paymentOperation);
        payment.setLastError(detail);
        payment.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void failInit(Payment payment, PaymentGateway.Outcome outcome, String detail) {
        paymentEventStore.append(payment.getPaymentId(), PaymentEventType.OPERATION_FAILED, new OperationFailedEvent(Operation.INIT, outcome.name(), detail));
        payment.setStatus(PaymentStatus.INIT_FAILED);
        payment.setLastError(outcome + ": " + detail);
        clearRetry(payment);
        paymentRepository.save(payment);
        release(payment);
    }

    private void retryOrStuck(Payment payment, String retryError, String stuckReason) {
        int next = payment.getRetryCount() + 1;
        if (next > MAX_RETRIES) {
            paymentEventStore.append(payment.getPaymentId(), PaymentEventType.PAYMENT_MARKED_STUCK, new PaymentMarkedStuckEvent(stuckReason));
            payment.setStatus(PaymentStatus.STUCK);
            payment.setLastError(stuckReason);
            payment.setNextRetryAt(null);
        } else {
            payment.setRetryCount(next);
            payment.setLastError(retryError);
            payment.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(600, 30L * (1L << next))));
        }
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void clearRetry(Payment p) {
        p.setTargetOperation(null);
        p.setRetryCount(0);
        p.setNextRetryAt(null);
        p.setUpdatedAt(LocalDateTime.now());
    }

    private void release(Payment p) {
        Reservation r = p.getReservation();
        r.setStatus(ReservationStatus.HELD);
        r.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(r);
    }

    private void confirmed(Payment payment) {
        paymentEventStore.append(payment.getPaymentId(), PaymentEventType.PAYMENT_CONFIRMED, new PaymentConfirmedEvent());
        payment.setStatus(PaymentStatus.CONFIRMED);
        clearRetry(payment);
        payment.setLastError(null);
        paymentRepository.save(payment);
        Reservation reservation = payment.getReservation();
        User user = payment.getUser();
        LocalDateTime now = LocalDateTime.now();
        reservation.setStatus(ReservationStatus.PAID);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);
        List<Seat> seats = reservation.getSeats().stream().map(ReservationSeat::getSeat).toList();
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.PAID);
            seat.setUpdatedAt(now);
        }
        seatRepository.saveAll(seats);
        List<Long> seatIds = seats.stream().map(Seat::getSeatId).toList();
        seatLockPort.unlock(seatIds);
        outboxEventWriter.write("Reservation", reservation.getReservationId().toString(), "SEAT_CONFIRMED", new SeatEventPayload(reservation.getReservationId(), seatIds, user.getUserId(), reservation.getEvent().getEventId(), LocalDateTime.now()));
    }

    private Payment require(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new NotFoundException("Payment not found: " + id));
    }

    private String key(Payment payment, PaymentOperation operation) {
        return payment.getOperationId() + "-" + operation.name();
    }

    private PaymentResponse concurrent(Long id, Exception e) {
        log.warn("Payment {} advanced concurrently", id, e);
        return response(require(id));
    }

    private PaymentResponse response(Payment p) {
        return new PaymentResponse(p.getPaymentId(), p.getStatus(), p.getAmount(), p.getProviderReference(), p.getRetryCount(), p.getLastError());
    }
}
