package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.SeatHistoryDto.SeatHistoryResponse;
import ro.midra.ticketing.application.service.SeatHistoryService;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatHistoryService seatHistoryService;

    @GetMapping("/{seatId}/history")
    public ResponseEntity<SeatHistoryResponse> getHistory(@PathVariable Long seatId) {
        return ResponseEntity.ok(seatHistoryService.getHistory(seatId));
    }
}
