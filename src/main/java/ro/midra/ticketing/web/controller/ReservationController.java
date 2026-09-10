package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.service.SeatHoldService;
import ro.midra.ticketing.payment.application.PaymentHistoryQueryService;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final SeatHoldService seatHoldService;
    private final PaymentHistoryQueryService paymentHistory;

    @PostMapping("/hold")
    public ResponseEntity<HoldSeatsResponse> holdSeats(@RequestBody HoldSeatsRequest request) {
        HoldSeatsResponse response = seatHoldService.holdSeats(request);

        // SUCCEEDED means Redis was up and MySQL resolved it synchronously in this call.
        // PROCESSING means Redis was down and the request was queued via Kafka; the client
        // should poll GET /api/reservations/status/{requestId} until it flips.
        // FAILED is a conflict, consistent with synchronous seat rejection, rather than still processing.
        HttpStatus status = switch (response.requestStatus()) {
            case SUCCEEDED -> HttpStatus.CREATED;
            case PROCESSING -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.CONFLICT;
        };

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<HoldSeatsResponse> getStatus(@PathVariable String requestId) {
        return ResponseEntity.ok(seatHoldService.getStatus(requestId));
    }

    @GetMapping("/{reservationId}/payments")
    public List<PaymentResponse> payments(@PathVariable Long reservationId) {
        return paymentHistory.history(reservationId);
    }
}
