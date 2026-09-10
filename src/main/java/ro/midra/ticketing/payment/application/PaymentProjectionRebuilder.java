package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.midra.ticketing.payment.application.port.PaymentEventStore;

@Service
@RequiredArgsConstructor
public class PaymentProjectionRebuilder {
    private final PaymentEventStore events;
    private final PaymentReplayService replay;
    /** Each stream commits separately; restarting this operation is safe. */
    public int rebuildAll() {
        long afterId = 0;
        int rebuilt = 0;
        while (true) {
            var ids = events.paymentIdsAfter(afterId, 100);
            if (ids.isEmpty()) return rebuilt;
            for (Long id : ids) { replay.rebuild(id); afterId = id; rebuilt++; }
        }
    }
}
