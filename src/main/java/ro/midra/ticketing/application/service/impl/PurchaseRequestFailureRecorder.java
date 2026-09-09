package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.service.StatusCachePort;
import ro.midra.ticketing.domain.PurchaseRequestStatus;
import ro.midra.ticketing.domain.repository.PurchaseRequestRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PurchaseRequestFailureRecorder {

    private static final Duration STATUS_CACHE_TTL = Duration.ofMinutes(5);

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final StatusCachePort statusCachePort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String requestId) {
        purchaseRequestRepository.findById(requestId).ifPresent(purchaseRequest -> {
            if (purchaseRequest.getStatus() == PurchaseRequestStatus.SUCCEEDED) {
                return;
            }

            purchaseRequest.setStatus(PurchaseRequestStatus.FAILED);
            purchaseRequest.setUpdatedAt(LocalDateTime.now());
            purchaseRequestRepository.save(purchaseRequest);
            statusCachePort.put(requestId,
                    new HoldSeatsResponse(requestId, PurchaseRequestStatus.FAILED, null, null, null, null),
                    STATUS_CACHE_TTL);
        });
    }
}
