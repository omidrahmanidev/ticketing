package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.ReservationSeat;

import java.util.List;

public interface ReservationSeatRepository {

    List<ReservationSeat> findBySeatSeatId(Long seatId);
}
