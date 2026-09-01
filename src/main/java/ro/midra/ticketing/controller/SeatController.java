package ro.midra.ticketing.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import ro.midra.ticketing.domain.Seat;
import ro.midra.ticketing.dto.SeatResponse;
import ro.midra.ticketing.repository.SeatRepository;
import ro.midra.ticketing.service.SeatCacheService;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatRepository seatRepository;
    private final SeatCacheService seatCacheService;

    @GetMapping("/{seatId}")
    public SeatResponse getSeat(
            @PathVariable Long eventId,
            @PathVariable Long seatId
    ) {

        /*
         * Redis is checked first.
         */
        String cachedStatus =
                seatCacheService.getSeatStatus(
                        eventId,
                        seatId
                );

        if (cachedStatus != null) {

            Seat seat = seatRepository
                    .findBySeatIdAndEvent_EventId(
                            seatId,
                            eventId
                    )
                    .orElseThrow();

            return new SeatResponse(
                    seat.getSeatId(),
                    eventId,
                    seat.getSeatNumber(),
                    seat.getStatus()
            );
        }

        /*
         * Redis down / cache miss:
         * fallback to MySQL.
         */
        Seat seat = seatRepository
                .findBySeatIdAndEvent_EventId(
                        seatId,
                        eventId
                )
                .orElseThrow();

        seatCacheService.cacheSeat(seat);

        return new SeatResponse(
                seat.getSeatId(),
                eventId,
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }
}