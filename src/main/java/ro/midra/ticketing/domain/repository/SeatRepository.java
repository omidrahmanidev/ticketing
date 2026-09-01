package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.Seat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository {

    Optional<Seat> findById(Long seatId);

    List<Seat> findByEventEventId(Long eventId);

    List<Seat> findAndLockByIds(List<Long> seatIds);

    Seat save(Seat seat);

    List<Seat> saveAll(Collection<Seat> seats);
}
