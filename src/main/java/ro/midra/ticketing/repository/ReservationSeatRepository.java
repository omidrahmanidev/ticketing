package ro.midra.ticketing.repository;

import ro.midra.ticketing.domain.ReservationSeat;
import ro.midra.ticketing.domain.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, ReservationSeatId> {

    List<ReservationSeat> findAllByReservation_ReservationId(
            Long reservationId
    );
}