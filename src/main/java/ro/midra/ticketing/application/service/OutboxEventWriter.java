package ro.midra.ticketing.application.service;

public interface OutboxEventWriter {

    void write(String aggregateType, String aggregateId, String eventType, Object payload);
}
