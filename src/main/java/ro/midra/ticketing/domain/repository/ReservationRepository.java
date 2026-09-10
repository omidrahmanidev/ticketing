package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    Optional<Reservation> findById(Long reservationId);

    Optional<Reservation> findAndLockById(Long reservationId);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime before);

    Reservation save(Reservation reservation);

    <S extends Reservation> List<S> saveAll(Iterable<S> reservations);
}
