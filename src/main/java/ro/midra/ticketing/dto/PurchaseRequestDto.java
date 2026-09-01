package ro.midra.ticketing.dto;

import java.util.List;

public record PurchaseRequestDto(
        String requestId,
        Long userId,
        Long eventId,
        List<Long> seatIds
) {
}
