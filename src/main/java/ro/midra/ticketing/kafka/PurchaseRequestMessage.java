package ro.midra.ticketing.kafka;

import java.util.List;

public record PurchaseRequestMessage(
        String requestId,
        Long userId,
        Long eventId,
        List<Long> seatIds
) {
}