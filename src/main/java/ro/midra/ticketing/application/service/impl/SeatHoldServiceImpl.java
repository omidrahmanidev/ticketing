package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsRequest;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.event.HoldSeatsCommandPayload;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.exception.SeatNotAvailableException;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.lock.SeatLockPort.SeatLockResult;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.application.service.SeatHoldService;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.domain.repository.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldServiceImpl implements SeatHoldService {

    private static final int HOLD_MINUTES = 10;

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SeatLockPort seatLockPort;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    @Transactional
    public HoldSeatsResponse holdSeats(HoldSeatsRequest request) {
        // idempotency: a retried client request with the same requestId is replayed, not re-executed
        Optional<PurchaseRequest> existing = purchaseRequestRepository.findById(request.requestId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + request.eventId()));

        LocalDateTime now = LocalDateTime.now();
        PurchaseRequest purchaseRequest = PurchaseRequest.builder()
                .requestId(request.requestId())
                .user(user)
                .event(event)
                .status(PurchaseRequestStatus.PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        purchaseRequestRepository.save(purchaseRequest);

        List<Long> sortedSeatIds = request.seatIds().stream().sorted().toList();

        // Redis SETNX-based lock. Rejects most competing requests in milliseconds without
        // touching MySQL. TTL matches the hold window so a crashed pod's lock still expires.
        SeatLockResult lockResult = seatLockPort.tryLock(sortedSeatIds, request.requestId(), Duration.ofMinutes(HOLD_MINUTES));

        if (lockResult.redisAvailable() && !lockResult.acquired()) {
            markFailed(purchaseRequest);
            throw new SeatNotAvailableException("Seat already locked: " + lockResult.conflictingSeatIds());
        }

        if (!lockResult.redisAvailable()) {
            // Redis is down: don't let the full request storm hit MySQL directly (it would fall
            // over under 500k concurrent requests). Instead, queue the work through the outbox
            // -> Kafka -> a small pool of consumer threads that write to MySQL at a controlled
            // rate. The purchase request stays PROCESSING until the consumer resolves it.
            outboxEventWriter.write(
                    "PurchaseRequest",
                    purchaseRequest.getRequestId(),
                    "HOLD_SEAT_COMMAND",
                    new HoldSeatsCommandPayload(request.requestId(), user.getUserId(), event.getEventId(), sortedSeatIds)
            );
            return toResponse(purchaseRequest);
        }

        // Redis is healthy and we hold the lock: resolve synchronously against MySQL, the
        // source of truth, right now.
        return finalizeHold(purchaseRequest, user, event, sortedSeatIds, true);
    }

    @Override
    @Transactional
    public void processQueuedHold(String requestId, List<Long> seatIds) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Purchase request not found: " + requestId));

        if (purchaseRequest.getStatus() != PurchaseRequestStatus.PROCESSING) {
            log.info("Purchase request {} already resolved as {}, skipping", requestId, purchaseRequest.getStatus());
            return;
        }

        try {
            finalizeHold(purchaseRequest, purchaseRequest.getUser(), purchaseRequest.getEvent(), seatIds, false);
        } catch (RuntimeException ex) {
            // no HTTP caller is waiting on this thread; the client discovers the failure by
            // polling getStatus(requestId). Swallow here so Kafka doesn't endlessly redeliver.
            log.warn("Queued seat hold failed for requestId={}: {}", requestId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HoldSeatsResponse getStatus(String requestId) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Purchase request not found: " + requestId));
        return toResponse(purchaseRequest);
    }

    /**
     * Source-of-truth layer: MySQL pessimistic lock + status check. Runs whether or not Redis
     * was involved, so a seat is never double-sold either way.
     */
    private HoldSeatsResponse finalizeHold(PurchaseRequest purchaseRequest, User user, Event event,
                                            List<Long> sortedSeatIds, boolean redisLockAcquired) {
        LocalDateTime now = LocalDateTime.now();
        try {
            List<Seat> seats = seatRepository.findAndLockByIds(sortedSeatIds);

            if (seats.size() != sortedSeatIds.size()) {
                throw new SeatNotAvailableException("One or more seats do not exist");
            }
            for (Seat seat : seats) {
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new SeatNotAvailableException("Seat not available: " + seat.getSeatId());
                }
            }

            Reservation reservation = Reservation.builder()
                    .event(event)
                    .user(user)
                    .status(ReservationStatus.HELD)
                    .expiresAt(now.plusMinutes(HOLD_MINUTES))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.HELD);
                seat.setActiveReservation(reservation);
                seat.setUpdatedAt(now);

                reservation.getSeats().add(
                        ReservationSeat.builder()
                                .id(new ReservationSeatId(null, seat.getSeatId()))
                                .reservation(reservation)
                                .seat(seat)
                                .build()
                );
                purchaseRequest.getSeats().add(
                        PurchaseRequestSeat.builder()
                                .id(new PurchaseRequestSeatId(purchaseRequest.getRequestId(), seat.getSeatId()))
                                .purchaseRequest(purchaseRequest)
                                .seat(seat)
                                .build()
                );
            }

            reservationRepository.save(reservation);
            seatRepository.saveAll(seats);

            purchaseRequest.setReservation(reservation);
            purchaseRequest.setStatus(PurchaseRequestStatus.SUCCEEDED);
            purchaseRequest.setUpdatedAt(LocalDateTime.now());
            purchaseRequestRepository.save(purchaseRequest);

            List<Long> seatIds = seats.stream().map(Seat::getSeatId).toList();

            outboxEventWriter.write(
                    "Reservation",
                    reservation.getReservationId().toString(),
                    "SEAT_HELD",
                    new SeatEventPayload(reservation.getReservationId(), seatIds, user.getUserId(),
                            event.getEventId(), LocalDateTime.now())
            );

            return toResponse(purchaseRequest);

        } catch (RuntimeException ex) {
            if (redisLockAcquired) {
                seatLockPort.unlock(sortedSeatIds);
            }
            markFailed(purchaseRequest);
            throw ex;
        }
    }

    private void markFailed(PurchaseRequest purchaseRequest) {
        purchaseRequest.setStatus(PurchaseRequestStatus.FAILED);
        purchaseRequest.setUpdatedAt(LocalDateTime.now());
        purchaseRequestRepository.save(purchaseRequest);
    }

    private HoldSeatsResponse toResponse(PurchaseRequest purchaseRequest) {
        if (purchaseRequest.getStatus() == PurchaseRequestStatus.SUCCEEDED) {
            Reservation reservation = purchaseRequest.getReservation();
            List<Long> seatIds = reservation.getSeats().stream()
                    .map(reservationSeat -> reservationSeat.getSeat().getSeatId())
                    .toList();
            return new HoldSeatsResponse(purchaseRequest.getRequestId(), purchaseRequest.getStatus(),
                    reservation.getReservationId(), reservation.getStatus(), reservation.getExpiresAt(), seatIds);
        }
        // PROCESSING or FAILED: no reservation to report yet (or ever)
        return new HoldSeatsResponse(purchaseRequest.getRequestId(), purchaseRequest.getStatus(),
                null, null, null, null);
    }
}
