package ro.midra.ticketing.repository;

import ro.midra.ticketing.domain.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequest, String> {
}