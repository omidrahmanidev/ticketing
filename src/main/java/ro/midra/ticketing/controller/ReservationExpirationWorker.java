package ro.midra.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationExpirationWorker {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 5000)
    public void expireReservations() {

        List<Reservation> expired =
                reservationRepository
                        .findAllByStatusAndExpiresAtLessThanEqual(
                                ReservationStatus.HELD,
                                LocalDateTime.now()
                        );

        for (Reservation reservation : expired) {

            try {
                reservationService.expireReservation(
                        reservation
                );
            } catch (Exception ignored) {
                /*
                 * Another worker/pod can retry it.
                 */
            }
        }
    }
}