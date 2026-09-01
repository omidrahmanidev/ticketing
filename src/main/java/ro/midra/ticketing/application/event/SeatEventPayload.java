package ro.midra.ticketing.application.event;

import java.time.LocalDateTime;
import java.util.List;

public record SeatEventPayload(
        Long reservationId,
        List<Long> seatIds,
        Long userId,
        Long eventId,
        LocalDateTime occurredAt
) {
}
