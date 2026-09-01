package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.EventDto.EventResponse;
import ro.midra.ticketing.application.dto.EventDto.SeatResponse;

import java.util.List;

public interface EventService {

    List<EventResponse> listOnSaleEvents();

    EventResponse getEvent(Long eventId);

    List<SeatResponse> listSeats(Long eventId);
}
