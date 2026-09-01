package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.event.SeatEventPayload;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.application.service.ReservationExpiryService;
import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationSeat;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.domain.Seat;
import ro.midra.ticketing.domain.SeatStatus;
import ro.midra.ticketing.domain.repository.ReservationRepository;
import ro.midra.ticketing.domain.repository.SeatRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationExpiryServiceImpl implements ReservationExpiryService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final SeatLockPort seatLockPort;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    @Scheduled(fixedRate = 30_000)
    @SchedulerLock(name = "releaseExpiredReservations", lockAtMostFor = "PT25S", lockAtLeastFor = "PT1S")
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expired = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.HELD, now);

        for (Reservation reservation : expired) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setUpdatedAt(now);

            List<Seat> seats = reservation.getSeats().stream()
                    .map(ReservationSeat::getSeat)
                    .toList();
            for (Seat seat : seats) {
                if (seat.getStatus() == SeatStatus.HELD) {
                    seat.setStatus(SeatStatus.AVAILABLE);
                    seat.setActiveReservation(null);
                    seat.setUpdatedAt(now);
                }
            }
            seatRepository.saveAll(seats);

            List<Long> seatIds = seats.stream().map(Seat::getSeatId).toList();
            // release the redis lock keys too, in case they somehow outlived their TTL
            seatLockPort.unlock(seatIds);

            outboxEventWriter.write(
                    "Reservation",
                    reservation.getReservationId().toString(),
                    "RESERVATION_EXPIRED",
                    new SeatEventPayload(reservation.getReservationId(), seatIds, reservation.getUser().getUserId(),
                            reservation.getEvent().getEventId(), LocalDateTime.now())
            );
        }
        reservationRepository.saveAll(expired);
    }
}
