package ro.midra.ticketing.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long eventId,
        Long userId,
        String status,
        LocalDateTime expiresAt,
        List<Long> seatIds
) {
}