package ro.midra.ticketing.dto;

public record PurchaseRequestResponse(
        String requestId,
        String status,
        Long reservationId
) {
}