package ro.midra.ticketing.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.repository.*;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int RESERVATION_MINUTES = 10;

    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatCacheService seatCacheService;

    @Transactional
    public Reservation createReservation(
            String requestId,
            Long userId,
            Long eventId,
            List<Long> seatIds
    ) {

        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("No seats selected");
        }

        List<Long> uniqueSeatIds = seatIds.stream()
                .distinct()
                .sorted()
                .toList();

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        /*
         * PESSIMISTIC LOCK
         *
         * SELECT ... FOR UPDATE
         *
         * The lock exists only during this DB transaction.
         * It is NOT a 10-minute DB lock.
         */
        List<Seat> seats = seatRepository.findSeatsForUpdate(
                eventId,
                uniqueSeatIds
        );

        if (seats.size() != uniqueSeatIds.size()) {
            throw new IllegalArgumentException(
                    "One or more seats do not exist"
            );
        }

        /*
         * All-or-nothing:
         * if even one seat is unavailable,
         * the whole reservation fails.
         */
        for (Seat seat : seats) {

            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException(
                        "Seat " + seat.getSeatNumber() +
                                " is not available"
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt =
                now.plusMinutes(RESERVATION_MINUTES);

        Reservation reservation = Reservation.builder()
                .event(seats.get(0).getEvent())
                .user(user)
                .status(ReservationStatus.HELD)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        reservationRepository.save(reservation);

        for (Seat seat : seats) {

            seat.setStatus(SeatStatus.HELD);
            seat.setActiveReservation(reservation);
            seat.setUpdatedAt(now);

            ReservationSeat reservationSeat =
                    ReservationSeat.builder()
                            .id(
                                    new ReservationSeatId(
                                            reservation.getReservationId(),
                                            seat.getSeatId()
                                    )
                            )
                            .reservation(reservation)
                            .seat(seat)
                            .build();

            reservationSeatRepository.save(reservationSeat);
        }

        /*
         * DB transaction is the source of truth.
         * Redis is only a cache.
         */
        for (Seat seat : seats) {
            seatCacheService.cacheSeat(seat);
        }

        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(
            Long reservationId,
            Long userId
    ) {

        return reservationRepository
                .findByReservationIdAndUser_UserId(
                        reservationId,
                        userId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reservation not found"
                        ));
    }

    @Transactional
    public void expireReservation(Reservation reservation) {

        if (reservation.getStatus() != ReservationStatus.HELD) {
            return;
        }

        if (reservation.getExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }

        List<ReservationSeat> reservationSeats =
                reservationSeatRepository
                        .findAllByReservation_ReservationId(
                                reservation.getReservationId()
                        );

        for (ReservationSeat reservationSeat : reservationSeats) {

            Seat seat = reservationSeat.getSeat();

            /*
             * Reservation is still the active owner.
             * No other reservation can overwrite this state
             * while this transaction is modifying it.
             */
            if (seat.getActiveReservation() != null &&
                    seat.getActiveReservation()
                            .getReservationId()
                            .equals(reservation.getReservationId())) {

                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setActiveReservation(null);
                seat.setUpdatedAt(LocalDateTime.now());

                seatCacheService.cacheSeat(seat);
            }
        }

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservation.setUpdatedAt(LocalDateTime.now());
    }
}