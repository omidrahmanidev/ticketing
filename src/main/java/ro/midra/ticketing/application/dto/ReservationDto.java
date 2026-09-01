package ro.midra.ticketing.application.dto;

import ro.midra.ticketing.domain.PurchaseRequestStatus;
import ro.midra.ticketing.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;

public class ReservationDto {

    public record HoldSeatsRequest(String requestId, Long userId, Long eventId, List<Long> seatIds) {
    }

    /**
     * requestStatus tells the client which case they are in:
     * PROCESSING -> still being worked on (sync fast-path in flight, or queued behind Kafka
     *               because Redis was down); poll the status endpoint again.
     * SUCCEEDED  -> reservationId/reservationStatus/expiresAt/seatIds are populated.
     * FAILED     -> the seat(s) could not be held; nothing else is populated.
     */
    public record HoldSeatsResponse(
            String requestId,
            PurchaseRequestStatus requestStatus,
            Long reservationId,
            ReservationStatus reservationStatus,
            LocalDateTime expiresAt,
            List<Long> seatIds
    ) {
    }

    private ReservationDto() {
    }
}
