package ro.midra.ticketing.payment.application.port;

import ro.midra.ticketing.payment.domain.PaymentState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentProjection {
    void project(PaymentEventStream stream);
    Optional<PaymentState> find(Long paymentId);
    List<Long> due(LocalDateTime now);
}
