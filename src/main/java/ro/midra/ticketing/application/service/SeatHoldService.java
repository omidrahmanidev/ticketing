package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;

import java.util.List;

public interface SeatHoldService {

    /**
     * Called synchronously from the controller. If Redis is healthy this resolves the seat
     * hold immediately (requestStatus SUCCEEDED/FAILED). If Redis is down it enqueues the
     * work via the outbox/Kafka and returns immediately with requestStatus PROCESSING.
     */
    HoldSeatsResponse holdSeats(HoldSeatsRequest request);

    /**
     * Called by SeatHoldCommandConsumer for requests that were queued because Redis was down.
     * Runs the same DB source-of-truth logic as the synchronous path, just off the Kafka
     * consumer thread instead of the caller's HTTP thread.
     */
    void processQueuedHold(String requestId, List<Long> seatIds);

    /**
     * Polled by the client after a PROCESSING response, until requestStatus becomes
     * SUCCEEDED or FAILED.
     */
    HoldSeatsResponse getStatus(String requestId);
}
