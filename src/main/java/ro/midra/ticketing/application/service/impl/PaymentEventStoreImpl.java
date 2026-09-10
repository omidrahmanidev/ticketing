package ro.midra.ticketing.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.exception.ConcurrentPaymentModificationException;
import ro.midra.ticketing.application.projection.PaymentProjectionState;
import ro.midra.ticketing.application.service.PaymentEventStore;
import ro.midra.ticketing.domain.PaymentEvent;
import ro.midra.ticketing.domain.PaymentEventType;
import ro.midra.ticketing.domain.repository.PaymentEventRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class PaymentEventStoreImpl implements PaymentEventStore {

    private final PaymentEventRepository paymentEventRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Override
    public void append(Long paymentId, PaymentEventType type, Object payload) {
        try {
            paymentEventRepository.save(PaymentEvent.builder()
                    .paymentId(paymentId)
                    .sequenceNumber(paymentEventRepository.countByPaymentId(paymentId) + 1)
                    .eventType(type)
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(LocalDateTime.now())
                    .build());
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConcurrentPaymentModificationException("Payment event was appended concurrently: " + paymentId, ex);
        } catch (PersistenceException ex) {
            // The EntityManager flush can expose the unique sequence constraint directly.
            throw new ConcurrentPaymentModificationException("Payment event was appended concurrently: " + paymentId, ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize payment event payload", ex);
        }
    }

    @Override
    public PaymentProjectionState replay(Long paymentId) {
        return PaymentProjectionState.fold(paymentEventRepository.findByPaymentIdOrderBySequenceNumberAsc(paymentId));
    }
}
