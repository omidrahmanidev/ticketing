package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.domain.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationJpaRepository extends JpaRepository<Reservation, Long>, ReservationRepository {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Reservation r where r.reservationId = :reservationId")
    java.util.Optional<Reservation> findAndLockById(Long reservationId);

    @Override
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime before);
}
