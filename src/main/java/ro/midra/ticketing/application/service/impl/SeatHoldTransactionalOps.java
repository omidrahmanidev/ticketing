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
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.application.service.StatusCachePort;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.domain.repository.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * This class is a separate Spring bean (not just a private method on SeatHoldServiceImpl) on
 * purpose: {@code @Transactional} only works through the Spring proxy, which only intercepts
 * calls coming from OTHER beans. If this logic lived as a private method called from within
 * SeatHoldServiceImpl itself (self-invocation), the proxy would never see the call and the
 * {@code @Transactional} annotations below would silently do nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SeatHoldTransactionalOps {

    private static final int HOLD_MINUTES = 10;
    private static final Duration STATUS_CACHE_TTL = Duration.ofMinutes(5);

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SeatLockPort seatLockPort;
    private final OutboxEventWriter outboxEventWriter;
    private final PurchaseRequestCreator purchaseRequestCreator;
    private final PurchaseRequestFailureRecorder purchaseRequestFailureRecorder;
    private final StatusCachePort statusCachePort;

    /**
     * Called only for requests that already won (or already own) the Redis lock. This is the
     * small fraction of the 500k requests that actually reaches MySQL on the request thread.
     */
    @Transactional
    HoldSeatsResponse createAndFinalize(HoldSeatsRequest request, List<Long> sortedSeatIds) {
        // idempotency: a retried client request with the same requestId is replayed, not
        // re-executed. Reached here only for winners/owners, so this DB read is cheap overall.
        Optional<PurchaseRequest> existing = purchaseRequestRepository.findById(request.requestId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + request.eventId()));

        PurchaseRequest purchaseRequest = newPurchaseRequest(request.requestId(), user, event);
        purchaseRequestCreator.create(purchaseRequest);

        return finalizeHold(purchaseRequest, user, event, sortedSeatIds, true);
    }

    /**
     * Called by the Kafka consumer when Redis was down at request time. Runs the four DB reads
     * (idempotency check, user, event) plus the insert here -- off the HTTP request thread,
     * at whatever rate the consumer pool is configured for, instead of at 500k requests/sec.
     */
    @Transactional
    void processQueuedHold(HoldSeatsCommandPayload command) {
        Optional<PurchaseRequest> existing = purchaseRequestRepository.findById(command.requestId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() != PurchaseRequestStatus.PROCESSING) {
                log.info("Purchase request {} already resolved as {}, skipping",
                        command.requestId(), existing.get().getStatus());
            }
            return;
        }

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + command.userId()));
        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + command.eventId()));

        PurchaseRequest purchaseRequest = newPurchaseRequest(command.requestId(), user, event);
        purchaseRequestCreator.create(purchaseRequest);

        try {
            finalizeHold(purchaseRequest, user, event, command.seatIds(), false);
        } catch (RuntimeException ex) {
            // no HTTP caller is waiting on this thread; the client discovers the failure by
            // polling getStatus(requestId). Swallow here so Kafka doesn't endlessly redeliver.
            log.warn("Queued seat hold failed for requestId={}: {}", command.requestId(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    HoldSeatsResponse getStatus(String requestId) {
        Optional<HoldSeatsResponse> cached = statusCachePort.get(requestId);
        if (cached.isPresent()) {
            return cached.get();
        }

        PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Purchase request not found: " + requestId));
        HoldSeatsResponse response = toResponse(purchaseRequest);
        if (purchaseRequest.getStatus() == PurchaseRequestStatus.SUCCEEDED
                || purchaseRequest.getStatus() == PurchaseRequestStatus.FAILED) {
            statusCachePort.put(requestId, response, STATUS_CACHE_TTL);
        }
        return response;
    }

    private PurchaseRequest newPurchaseRequest(String requestId, User user, Event event) {
        LocalDateTime now = LocalDateTime.now();
        return PurchaseRequest.builder()
                .requestId(requestId)
                .user(user)
                .event(event)
                .status(PurchaseRequestStatus.PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();
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

            HoldSeatsResponse response = toResponse(purchaseRequest);
            statusCachePort.put(purchaseRequest.getRequestId(), response, STATUS_CACHE_TTL);
            return response;

        } catch (RuntimeException ex) {
            if (redisLockAcquired) {
                seatLockPort.unlock(sortedSeatIds);
            }
            purchaseRequestFailureRecorder.markFailed(purchaseRequest.getRequestId());
            throw ex;
        }
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
        return new HoldSeatsResponse(purchaseRequest.getRequestId(), purchaseRequest.getStatus(),
                null, null, null, null);
    }
}
