package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.domain.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxDeliveryTransactions {
    private final OutboxEventRepository events;
    @Transactional(readOnly = true)
    public List<OutboxEvent> batch() { return events.findDue(LocalDateTime.now(), PageRequest.of(0, 100)); }
    @Transactional
    public void acknowledge(Long id) {
        var event = events.findAndLockById(id).orElseThrow();
        event.setPublished(true);
        events.save(event);
    }
    @Transactional
    public void retryLater(Long id) {
        var event = events.findAndLockById(id).orElseThrow();
        if (event.isPublished()) return;
        int attempts = event.getDeliveryAttempts() == null ? 1 : event.getDeliveryAttempts() + 1;
        event.setDeliveryAttempts(attempts);
        event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
        events.save(event);
    }
}
