package ro.midra.ticketing.payment.infrastructure.eventstore;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.exception.ConcurrentPaymentModificationException;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.payment.application.port.*;
import ro.midra.ticketing.payment.domain.PaymentEvent;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaPaymentEventStore implements PaymentEventStore {
    private final PaymentEventJpaRepository events;
    private final PaymentEventSerializer serializer;

    @Override
    @Transactional(readOnly = true)
    public PaymentEventStream load(Long paymentId) {
        var rows = events.findByPaymentIdOrderBySequenceNumberAsc(paymentId);
        if (rows.isEmpty()) throw new NotFoundException("Payment not found: " + paymentId);
        long sequence = 0;
        for (var row : rows) {
            if (row.getSequenceNumber() != ++sequence) throw new IllegalStateException("Gap in payment stream " + paymentId);
        }
        var stream = new PaymentEventStream(paymentId, rows.stream().map(serializer::deserialize).toList());
        if (!paymentId.equals(stream.aggregate().state().paymentId())) throw new IllegalStateException("Payment stream identity mismatch");
        return stream;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Long paymentId, long expectedVersion, List<PaymentEvent> pending) {
        if (pending.isEmpty()) return;
        if (expectedVersion < 0 || events.currentVersion(paymentId) != expectedVersion) throw conflict(paymentId, null);
        long sequence = expectedVersion;
        try {
            for (PaymentEvent event : pending) {
                if (event instanceof PaymentEvent.PaymentInitiated initiated
                        && (!paymentId.equals(initiated.paymentId()) || sequence != 0)) {
                    throw new IllegalArgumentException("Invalid initiation identity/version");
                }
                events.save(new PaymentEventEntity(paymentId, ++sequence, event.getClass().getSimpleName(),
                        serializer.serialize(event), event.occurredAt()));
            }
            // Unique(payment_id, sequence_number), rather than a count, arbitrates concurrent writers.
            events.flush();
        } catch (DataIntegrityViolationException ex) {
            if (uniqueViolation(ex)) throw conflict(paymentId, ex);
            throw ex;
        }
    }
    private boolean uniqueViolation(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) return true;
        }
        return false;
    }
    private ConcurrentPaymentModificationException conflict(Long id, Throwable cause) {
        return new ConcurrentPaymentModificationException("Payment stream advanced concurrently: " + id, cause);
    }
    @Override
    @Transactional(readOnly = true)
    public List<Long> paymentIdsAfter(long afterId, int limit) {
        return events.paymentIdsAfter(afterId, PageRequest.of(0, limit));
    }
}
