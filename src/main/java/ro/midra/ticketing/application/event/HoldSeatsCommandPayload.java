package ro.midra.ticketing.application.event;

import java.util.List;

public record HoldSeatsCommandPayload(
        String requestId,
        Long userId,
        Long eventId,
        List<Long> seatIds
) {
}
