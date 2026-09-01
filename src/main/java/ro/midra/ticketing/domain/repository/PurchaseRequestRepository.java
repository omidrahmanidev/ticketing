package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.PurchaseRequest;

import java.util.Optional;

public interface PurchaseRequestRepository {

    Optional<PurchaseRequest> findById(String requestId);

    PurchaseRequest save(PurchaseRequest purchaseRequest);
}
