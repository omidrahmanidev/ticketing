package ro.midra.ticketing.repository;

import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByReservationIdAndUser_UserId(
            Long reservationId,
            Long userId
    );

    List<Reservation> findAllByStatusAndExpiresAtLessThanEqual(
            ReservationStatus status,
            java.time.LocalDateTime expiresAt
    );

    boolean existsByUser_UserIdAndEvent_EventIdAndStatus(
            Long userId,
            Long eventId,
            ReservationStatus status
    );
}