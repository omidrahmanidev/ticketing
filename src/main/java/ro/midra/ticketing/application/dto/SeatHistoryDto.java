package ro.midra.ticketing.application.dto;

import ro.midra.ticketing.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;

public class SeatHistoryDto {

    public record SeatHistoryEntry(
            Long reservationId,
            Long userId,
            ReservationStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
    }

    public record SeatHistoryResponse(Long seatId, String seatNumber, List<SeatHistoryEntry> history) {
    }

    private SeatHistoryDto() {
    }
}
