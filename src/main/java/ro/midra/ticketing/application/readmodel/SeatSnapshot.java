package ro.midra.ticketing.application.readmodel;

import ro.midra.ticketing.domain.SeatStatus;

public record SeatSnapshot(Long seatId, String seatNumber, SeatStatus status) {
}
