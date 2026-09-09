package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.readmodel.SeatSnapshot;
import ro.midra.ticketing.domain.SeatStatus;

import java.util.List;
import java.util.Optional;

public interface SeatReadModelPort {

    void upsertSeats(Long eventId, List<SeatSnapshot> seats);

    void updateStatus(Long eventId, Long seatId, SeatStatus status);

    Optional<List<SeatSnapshot>> listSeats(Long eventId);
}
