package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.exception.InvalidReservationStateException;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.application.service.PaymentService;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.domain.repository.PaymentRepository;
import ro.midra.ticketing.domain.repository.ReservationRepository;
import ro.midra.ticketing.domain.repository.SeatRepository;
import ro.midra.ticketing.domain.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SeatLockPort seatLockPort;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    @Transactional
    public PaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + request.reservationId()));

        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new InvalidReservationStateException(
                    "Reservation is not in HELD state: " + reservation.getReservationId());
        }
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidReservationStateException(
                    "Reservation hold already expired: " + reservation.getReservationId());
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));

        LocalDateTime now = LocalDateTime.now();

        Payment payment = Payment.builder()
                .reservation(reservation)
                .user(user)
                .amount(BigDecimal.valueOf(reservation.getSeats().size()))
                .status(PaymentStatus.SUCCEEDED)
                .providerReference(request.providerReference())
                .createdAt(now)
                .updatedAt(now)
                .build();
        paymentRepository.save(payment);

        reservation.setStatus(ReservationStatus.PAID);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);

        List<Seat> seats = reservation.getSeats().stream().map(ReservationSeat::getSeat).toList();
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.PAID);
            seat.setUpdatedAt(now);
        }
        seatRepository.saveAll(seats);

        // seat is permanently sold now, no need to keep the redis lock key around
        List<Long> seatIds = seats.stream().map(Seat::getSeatId).toList();
        seatLockPort.unlock(seatIds);

        outboxEventWriter.write(
                "Reservation",
                reservation.getReservationId().toString(),
                "SEAT_CONFIRMED",
                new SeatEventPayload(reservation.getReservationId(), seatIds, user.getUserId(),
                        reservation.getEvent().getEventId(), LocalDateTime.now())
        );

        return new PaymentResponse(payment.getPaymentId(), payment.getStatus(), payment.getAmount());
    }
}
