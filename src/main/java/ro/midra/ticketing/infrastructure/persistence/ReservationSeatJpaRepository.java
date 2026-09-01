package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.ReservationSeat;
import ro.midra.ticketing.domain.ReservationSeatId;
import ro.midra.ticketing.domain.repository.ReservationSeatRepository;

import java.util.List;

public interface ReservationSeatJpaRepository
        extends JpaRepository<ReservationSeat, ReservationSeatId>, ReservationSeatRepository {

    @Override
    List<ReservationSeat> findBySeatSeatId(Long seatId);
}
