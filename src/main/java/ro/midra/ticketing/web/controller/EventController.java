package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.EventDto.EventResponse;
import ro.midra.ticketing.application.dto.EventDto.SeatResponse;
import ro.midra.ticketing.application.service.EventService;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> listOnSaleEvents() {
        return ResponseEntity.ok(eventService.listOnSaleEvents());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }

    @GetMapping("/{eventId}/seats")
    public ResponseEntity<List<SeatResponse>> listSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.listSeats(eventId));
    }
}
