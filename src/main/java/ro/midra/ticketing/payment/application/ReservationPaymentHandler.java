package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.domain.repository.*;
import ro.midra.ticketing.payment.application.port.PaymentEventStore;
import ro.midra.ticketing.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Reservation owns its seats and handles a committed payment outcome idempotently. */
@Service
@RequiredArgsConstructor
public class ReservationPaymentHandler {
    private final PaymentEventStore events;
    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final PurchaseRequestRepository purchases;
    private final OutboxEventWriter outbox;

    @Transactional
    public void handle(Long paymentId, boolean confirmed) {
        var payment = events.load(paymentId).aggregate().state();
        if (confirmed ? payment.status() != PaymentStatus.CONFIRMED
                : payment.status() != PaymentStatus.INIT_FAILED && payment.status() != PaymentStatus.REFUNDED) return;
        var reservation = reservations.findAndLockById(payment.reservationId()).orElseThrow();
        if (reservation.getStatus() == ReservationStatus.PAID || reservation.getStatus() == ReservationStatus.CANCELLED) return;
        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING)
            throw new IllegalStateException("Unexpected reservation state for payment " + paymentId);
        if (!confirmed) {
            // Preserve the original hold deadline, seats and Redis locks for another payment attempt.
            reservation.setStatus(ReservationStatus.HELD);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservations.save(reservation);
            return;
        }
        var ids = reservation.getSeats().stream().map(rs -> rs.getSeat().getSeatId()).sorted().toList();
        var reservedSeats = seats.findAndLockByIds(ids);
        var now = LocalDateTime.now();
        for (Seat seat : reservedSeats) {
            if (seat.getActiveReservation() == null || !payment.reservationId().equals(seat.getActiveReservation().getReservationId()))
                throw new IllegalStateException("Seat no longer belongs to payment reservation " + paymentId);
            seat.setStatus(SeatStatus.PAID);
            seat.setUpdatedAt(now);
        }
        seats.saveAll(reservedSeats);
        reservation.setStatus(ReservationStatus.PAID);
        reservation.setUpdatedAt(now);
        reservations.save(reservation);
        outbox.write("Reservation", payment.reservationId().toString(), "SEAT_CONFIRMED",
                new SeatEventPayload(payment.reservationId(), ids, payment.userId(), reservation.getEvent().getEventId(), now));
        purchases.findByReservationReservationId(payment.reservationId()).ifPresent(purchase ->
                outbox.write("SeatLockRelease", payment.reservationId().toString(), "RELEASE_SEAT_LOCKS",
                        new ReleaseSeatLocks(ids, purchase.getRequestId())));
    }
    public record ReleaseSeatLocks(List<Long> seatIds, String owner) {}
}
