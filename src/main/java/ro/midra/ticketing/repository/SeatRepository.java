package ro.midra.ticketing.repository;

import ro.midra.ticketing.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByEvent_EventIdAndSeatIdInOrderBySeatIdAsc(
            Long eventId,
            List<Long> seatIds
    );

    Optional<Seat> findBySeatIdAndEvent_EventId(
            Long seatId,
            Long eventId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM Seat s
        WHERE s.event.eventId = :eventId
          AND s.seatId IN :seatIds
        ORDER BY s.seatId ASC
    """)
    List<Seat> findSeatsForUpdate(
            @Param("eventId") Long eventId,
            @Param("seatIds") List<Long> seatIds
    );
}