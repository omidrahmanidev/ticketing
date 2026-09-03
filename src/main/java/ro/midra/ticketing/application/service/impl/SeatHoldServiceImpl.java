package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;
import ro.midra.ticketing.application.exception.SeatNotAvailableException;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.lock.SeatLockPort.SeatLockResult;
import ro.midra.ticketing.application.service.SeatHoldCommandPublisher;
import ro.midra.ticketing.application.service.SeatHoldService;
import ro.midra.ticketing.domain.PurchaseRequestStatus;

import java.time.Duration;
import java.util.List;

/**
 * Thin orchestrator. On purpose, it does NOT touch MySQL directly -- it only decides, using
 * Redis, whether this request should touch MySQL at all, and if so, delegates to
 * {@link SeatHoldTransactionalOps} (a separate bean, so its @Transactional methods actually
 * take effect -- see the note on that class).
 *
 * Result: out of 500,000 concurrent requests for 10,000 seats, only the requests that actually
 * win (or already own) a Redis lock ever reach MySQL. Everyone else is rejected in Redis,
 * with zero database access.
 */
@Service
@RequiredArgsConstructor
public class SeatHoldServiceImpl implements SeatHoldService {

    private static final int HOLD_MINUTES = 10;

    private final SeatLockPort seatLockPort;
    private final SeatHoldCommandPublisher seatHoldCommandPublisher;
    private final SeatHoldTransactionalOps transactionalOps;

    @Override
    public HoldSeatsResponse holdSeats(HoldSeatsRequest request) {
        List<Long> sortedSeatIds = request.seatIds().stream().sorted().toList();

        // Redis SETNX-based lock, tried FIRST, before any database access at all.
        SeatLockResult lockResult =
                seatLockPort.tryLock(sortedSeatIds, request.requestId(), Duration.ofMinutes(HOLD_MINUTES));

        if (lockResult.redisAvailable() && !lockResult.acquired()) {
            // Someone else already holds this seat. Rejected purely in Redis -- MySQL never
            // sees this request.
            throw new SeatNotAvailableException("Seat already locked: " + lockResult.conflictingSeatIds());
        }

        if (!lockResult.redisAvailable()) {
            // Redis is down. Do not query or write MySQL on this thread -- that is exactly
            // the 500k-requests-at-once problem we're avoiding. Send the raw command straight
            // to Kafka (no outbox here either, since outbox itself needs a DB write) and let
            // a small, fixed-size consumer pool do the four DB reads + one insert later, at a
            // controlled rate.
            seatHoldCommandPublisher.publish(
                    new HoldSeatsCommandPayload(request.requestId(), request.userId(), request.eventId(), sortedSeatIds)
            );
            return new HoldSeatsResponse(request.requestId(), PurchaseRequestStatus.PROCESSING,
                    null, null, null, null);
        }

        // Redis is healthy and we hold the lock (freshly acquired, or already ours from a
        // retry): this is the small fraction of requests allowed to touch MySQL.
        return transactionalOps.createAndFinalize(request, sortedSeatIds);
    }

    @Override
    public void processQueuedHold(HoldSeatsCommandPayload command) {
        transactionalOps.processQueuedHold(command);
    }

    @Override
    public HoldSeatsResponse getStatus(String requestId) {
        return transactionalOps.getStatus(requestId);
    }
}
