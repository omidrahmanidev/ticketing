package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.EventDto.EventResponse;
import ro.midra.ticketing.application.dto.EventDto.SeatResponse;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.service.EventService;
import ro.midra.ticketing.domain.Event;
import ro.midra.ticketing.domain.EventStatus;
import ro.midra.ticketing.domain.repository.EventRepository;
import ro.midra.ticketing.domain.repository.SeatRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Override
    public List<EventResponse> listOnSaleEvents() {
        return eventRepository.findByStatus(EventStatus.ON_SALE).stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Override
    public EventResponse getEvent(Long eventId) {
        return toEventResponse(findEventOrThrow(eventId));
    }

    @Override
    public List<SeatResponse> listSeats(Long eventId) {
        findEventOrThrow(eventId);
        return seatRepository.findByEventEventId(eventId).stream()
                .map(seat -> new SeatResponse(seat.getSeatId(), seat.getSeatNumber(), seat.getStatus()))
                .toList();
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    private EventResponse toEventResponse(Event event) {
        return new EventResponse(event.getEventId(), event.getName(), event.getStartsAt(), event.getStatus());
    }
}
