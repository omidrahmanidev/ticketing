package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.payment.application.PaymentStepTransactions;
import ro.midra.ticketing.payment.application.port.PaymentProjection;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryJob {
    private final PaymentProjection projection;
    private final PaymentStepTransactions sagaSteps;
    private final Clock paymentClock;

    @Scheduled(fixedDelay = 20_000)
    @SchedulerLock(name = "recoverPayments", lockAtMostFor = "PT15S", lockAtLeastFor = "PT1S")
    public void recover() {
        for (Long id : projection.due(LocalDateTime.now(paymentClock))) {
            try {
                sagaSteps.recover(id);
            } catch (RuntimeException ex) {
                log.warn("Could not recover paymentId={}", id, ex);
            }
        }
    }
}
