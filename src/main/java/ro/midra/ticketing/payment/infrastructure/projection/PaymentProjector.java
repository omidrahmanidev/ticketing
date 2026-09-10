package ro.midra.ticketing.payment.infrastructure.projection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.payment.application.port.*;
import ro.midra.ticketing.payment.domain.PaymentState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentProjector implements PaymentProjection {
    private final PaymentViewJpaRepository views;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void project(PaymentEventStream stream) {
        // One authoritative fold; this adapter only maps its result to query columns.
        // Serialize projection writes, including a rebuild racing a normal event append.
        entityManager.find(ro.midra.ticketing.payment.infrastructure.eventstore.PaymentIdentityEntity.class,
                stream.paymentId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var state = stream.aggregate().state();
        var existing = views.findById(stream.paymentId());
        if (existing.isEmpty() || existing.get().state().version() <= state.version())
            views.save(PaymentViewEntity.from(state));
    }
    @Transactional(readOnly = true)
    public Optional<PaymentState> find(Long paymentId) { return views.findById(paymentId).map(PaymentViewEntity::state); }
    @Transactional(readOnly = true)
    public List<Long> due(LocalDateTime now) { return views.due(now, PageRequest.of(0, 50)); }
}
