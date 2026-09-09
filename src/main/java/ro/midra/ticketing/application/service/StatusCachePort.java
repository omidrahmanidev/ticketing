package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;

import java.time.Duration;
import java.util.Optional;

public interface StatusCachePort {

    Optional<HoldSeatsResponse> get(String requestId);

    void put(String requestId, HoldSeatsResponse response, Duration ttl);
}
