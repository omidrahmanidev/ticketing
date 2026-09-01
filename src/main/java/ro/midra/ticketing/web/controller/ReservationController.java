package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.service.SeatHoldService;
import ro.midra.ticketing.domain.PurchaseRequestStatus;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/hold")
    public ResponseEntity<HoldSeatsResponse> holdSeats(@RequestBody HoldSeatsRequest request) {
        HoldSeatsResponse response = seatHoldService.holdSeats(request);

        // SUCCEEDED means Redis was up and MySQL resolved it synchronously in this call.
        // PROCESSING means Redis was down and the request was queued via Kafka; the client
        // should poll GET /api/reservations/status/{requestId} until it flips.
        HttpStatus status = response.requestStatus() == PurchaseRequestStatus.SUCCEEDED
                ? HttpStatus.CREATED
                : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<HoldSeatsResponse> getStatus(@PathVariable String requestId) {
        return ResponseEntity.ok(seatHoldService.getStatus(requestId));
    }
}
