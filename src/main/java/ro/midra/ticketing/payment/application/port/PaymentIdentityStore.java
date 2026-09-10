package ro.midra.ticketing.payment.application.port;

import java.util.List;
import java.util.Optional;

/** Identity/idempotency only; payment state is exclusively in the event stream. */
public interface PaymentIdentityStore {
    Long allocate(Long reservationId);
    /** Returns the most recently allocated payment ID, not necessarily an active payment. */
    Optional<Long> findLatestPaymentId(Long reservationId);
    List<Long> findAllPaymentIds(Long reservationId);
}
