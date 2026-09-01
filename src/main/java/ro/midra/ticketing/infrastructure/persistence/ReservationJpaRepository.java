package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.domain.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationJpaRepository extends JpaRepository<Reservation, Long>, ReservationRepository {

    @Override
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime before);
}
