package ro.midra.ticketing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.midra.ticketing.domain.Seat;
import ro.midra.ticketing.domain.repository.SeatRepository;

import java.util.List;

public interface SeatJpaRepository extends JpaRepository<Seat, Long>, SeatRepository {

    @Override
    List<Seat> findByEventEventId(Long eventId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.seatId in :seatIds order by s.seatId asc")
    List<Seat> findAndLockByIds(@Param("seatIds") List<Long> seatIds);
}
