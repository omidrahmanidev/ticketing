package ro.midra.ticketing.payment.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.payment.application.*;

@Component
@RequiredArgsConstructor
public class PaymentOutboxRouter {
    private final ObjectMapper mapper;
    private final PaymentCommandWorker worker;
    private final ReservationPaymentHandler reservations;
    private final SeatLockPort locks;

    public boolean deliver(OutboxEvent event) throws com.fasterxml.jackson.core.JsonProcessingException {
        switch (event.getAggregateType()) {
            case "PaymentCommand" -> {
                var command = mapper.readValue(event.getPayload(), PaymentCommand.class);
                if (!command.messageType().equals(event.getEventType())) throw new IllegalArgumentException("Payment command type mismatch");
                worker.execute(command);
            }
            case "PaymentIntegration" -> {
                boolean confirmed = switch (event.getEventType()) {
                    case "RESERVATION_PAYMENT_CONFIRMED" -> true;
                    case "RESERVATION_PAYMENT_FAILED" -> false;
                    default -> throw new IllegalArgumentException("Unknown payment integration " + event.getEventType());
                };
                var message = mapper.readValue(event.getPayload(), PaymentSaga.ReservationPaymentMessage.class);
                reservations.handle(message.paymentId(), confirmed);
            }
            case "SeatLockRelease" -> {
                var release = mapper.readValue(event.getPayload(), ReservationPaymentHandler.ReleaseSeatLocks.class);
                locks.unlockOwned(release.seatIds(), release.owner());
            }
            default -> { return false; }
        }
        return true;
    }
}
