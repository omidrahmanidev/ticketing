package ro.midra.ticketing.application.dto;

import ro.midra.ticketing.domain.EventStatus;
import ro.midra.ticketing.domain.SeatStatus;

import java.time.LocalDateTime;

public class EventDto {

    public record EventResponse(Long eventId, String name, LocalDateTime startsAt, EventStatus status) {
    }

    public record SeatResponse(Long seatId, String seatNumber, SeatStatus status) {
    }

    private EventDto() {
    }
}
