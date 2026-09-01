package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.PurchaseRequest;
import ro.midra.ticketing.domain.repository.PurchaseRequestRepository;

public interface PurchaseRequestJpaRepository
        extends JpaRepository<PurchaseRequest, String>, PurchaseRequestRepository {
}
