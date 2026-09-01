package ro.midra.ticketing.dto;

import ro.midra.ticketing.domain.SeatStatus;

public record SeatResponse(
        Long seatId,
        Long eventId,
        String seatNumber,
        SeatStatus status
) {
}