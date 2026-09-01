package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.SeatHistoryDto.SeatHistoryEntry;
import ro.midra.ticketing.application.dto.SeatHistoryDto.SeatHistoryResponse;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.service.SeatHistoryService;
import ro.midra.ticketing.domain.Reservation;
import ro.midra.ticketing.domain.Seat;
import ro.midra.ticketing.domain.repository.ReservationSeatRepository;
import ro.midra.ticketing.domain.repository.SeatRepository;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatHistoryServiceImpl implements SeatHistoryService {

    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    @Override
    public SeatHistoryResponse getHistory(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found: " + seatId));

        List<SeatHistoryEntry> history = reservationSeatRepository.findBySeatSeatId(seatId).stream()
                .map(reservationSeat -> reservationSeat.getReservation())
                .sorted(Comparator.comparing(Reservation::getCreatedAt).reversed())
                .map(reservation -> new SeatHistoryEntry(
                        reservation.getReservationId(),
                        reservation.getUser().getUserId(),
                        reservation.getStatus(),
                        reservation.getCreatedAt(),
                        reservation.getExpiresAt()
                ))
                .toList();

        return new SeatHistoryResponse(seat.getSeatId(), seat.getSeatNumber(), history);
    }
}
