package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.SeatHistoryDto.SeatHistoryResponse;

public interface SeatHistoryService {

    SeatHistoryResponse getHistory(Long seatId);
}
