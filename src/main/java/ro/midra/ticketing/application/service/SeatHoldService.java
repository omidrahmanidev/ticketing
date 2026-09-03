package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;

public interface SeatHoldService {

    /**
     * Called synchronously from the controller. If Redis is healthy this resolves the seat
     * hold immediately (requestStatus SUCCEEDED/FAILED). If Redis is down it publishes the
     * command straight to Kafka (no DB access on this thread) and returns immediately with
     * requestStatus PROCESSING.
     */
    HoldSeatsResponse holdSeats(HoldSeatsRequest request);

    /**
     * Called by SeatHoldCommandConsumer for requests that were queued because Redis was down.
     * This is where the DB reads/writes for that request actually happen, off the Kafka
     * consumer thread instead of the caller's HTTP thread, at a controlled rate.
     */
    void processQueuedHold(HoldSeatsCommandPayload command);

    /**
     * Polled by the client after a PROCESSING response, until requestStatus becomes
     * SUCCEEDED or FAILED.
     */
    HoldSeatsResponse getStatus(String requestId);
}
