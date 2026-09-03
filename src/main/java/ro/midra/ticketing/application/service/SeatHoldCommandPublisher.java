package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;

public interface SeatHoldCommandPublisher {

    /**
     * Sends the raw hold request straight to Kafka. No database read or write happens here --
     * that's the whole point: when Redis is down, this is the only thing the request thread
     * does, so MySQL never sees the full burst of traffic directly.
     */
    void publish(HoldSeatsCommandPayload command);
}
