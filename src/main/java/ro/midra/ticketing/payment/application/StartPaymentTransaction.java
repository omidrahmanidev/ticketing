package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.exception.InvalidReservationStateException;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.domain.repository.ReservationRepository;
import ro.midra.ticketing.domain.repository.UserRepository;
import ro.midra.ticketing.payment.application.port.PaymentEventStore;
import ro.midra.ticketing.payment.application.port.PaymentIdentityStore;
import ro.midra.ticketing.payment.domain.PaymentAggregate;
import ro.midra.ticketing.payment.domain.PaymentOperation;
import ro.midra.ticketing.payment.domain.PaymentState;
import ro.midra.ticketing.payment.domain.PaymentStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StartPaymentTransaction {
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PaymentIdentityStore paymentIdentityStore;
    private final PaymentEventStore events;
    private final PaymentAmountCalculator amountCalculator;
    private final PaymentSaga saga;
    private final PaymentCommitter committer;
    private final Clock paymentClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentState start(ConfirmPaymentRequest request) {
        if (request.reservationId() == null || request.userId() == null)
            throw new InvalidReservationStateException("Reservation and user are required");
        var reservation = reservationRepository.findAndLockById(request.reservationId())
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + request.reservationId()));
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        if (!reservation.getUser().getUserId().equals(user.getUserId()))
            throw new InvalidReservationStateException("Reservation does not belong to user: " + request.reservationId());
        // Ownership is checked even for idempotent repeats. No projection participates in this decision.
        // findAndLockById already pessimistically locks the reservation before identity lookup/allocation.
        // That lock serializes concurrent starts, not a reservation identity uniqueness constraint; no new lock is needed.
        var existing = paymentIdentityStore.findLatestPaymentId(request.reservationId());
        if (existing.isPresent()) {
            var existingState = events.load(existing.get()).aggregate().state();
            if (existingState.status() != PaymentStatus.INIT_FAILED && existingState.status() != PaymentStatus.REFUNDED)
                return existingState;
        }
        var now = LocalDateTime.now(paymentClock);
        if (reservation.getStatus() != ReservationStatus.HELD)
            throw new InvalidReservationStateException("Reservation is not in HELD state: " + request.reservationId());
        if (!reservation.getExpiresAt().isAfter(now))
            throw new InvalidReservationStateException("Reservation hold already expired: " + request.reservationId());
        var payment = PaymentAggregate.start(paymentIdentityStore.allocate(request.reservationId()), request.reservationId(),
                request.userId(), amountCalculator.total(reservation), "payment-" + UUID.randomUUID(), now);
        reservation.setStatus(ReservationStatus.PAYMENT_PENDING);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);
        saga.request(payment, PaymentOperation.INIT, request.testScenario(), now);
        committer.commit(payment);
        return payment.state();
    }
}
