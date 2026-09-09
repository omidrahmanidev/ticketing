package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.domain.PurchaseRequest;
import ro.midra.ticketing.domain.repository.PurchaseRequestRepository;

/**
 * Persists the idempotency record before seat processing starts. This lets the separate failure
 * transaction see and update the request if the enclosing seat-processing transaction rolls back.
 */
@Service
@RequiredArgsConstructor
class PurchaseRequestCreator {

    private final PurchaseRequestRepository purchaseRequestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurchaseRequest create(PurchaseRequest purchaseRequest) {
        return purchaseRequestRepository.save(purchaseRequest);
    }
}
