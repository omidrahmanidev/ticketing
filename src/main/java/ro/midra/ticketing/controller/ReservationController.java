package ro.midra.ticketing.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationSeat;
import ro.midra.ticketing.dto.ReservationResponse;
import ro.midra.ticketing.service.ReservationService;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/{reservationId}")
    public ReservationResponse getReservation(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {

        Reservation reservation =
                reservationService.getReservation(
                        reservationId,
                        userId
                );

        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getEvent().getEventId(),
                reservation.getUser().getUserId(),
                reservation.getStatus().name(),
                reservation.getExpiresAt(),
                reservation.getSeats()
                        .stream()
                        .map(ReservationSeat::getSeat)
                        .map(seat -> seat.getSeatId())
                        .toList()
        );
    }
}