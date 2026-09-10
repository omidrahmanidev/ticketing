package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.service.PaymentService;
import ro.midra.ticketing.domain.Payment;
import ro.midra.ticketing.domain.PaymentStatus;
import ro.midra.ticketing.domain.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryJob {
    private static final List<PaymentStatus> RECOVERABLE = List.of(PaymentStatus.INIT_PENDING,
            PaymentStatus.AWAITING_CONFIRM, PaymentStatus.INQUIRY_PENDING, PaymentStatus.REFUND_PENDING);
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedRate = 20_000)
    @SchedulerLock(name = "recoverPayments", lockAtMostFor = "PT15S", lockAtLeastFor = "PT1S")
    public void recover() {
        List<Payment> payments = new ArrayList<>(paymentRepository
                .findTop50ByStatusInAndNextRetryAtBeforeOrderByNextRetryAtAsc(RECOVERABLE, LocalDateTime.now()));
        if (payments.size() < 50) payments.addAll(paymentRepository
                .findTop50ByStatusInAndNextRetryAtIsNullOrderByPaymentIdAsc(RECOVERABLE));
        payments.stream().limit(50).forEach(payment -> {
            try {
                switch (payment.getStatus()) {
                    case INIT_PENDING -> paymentService.processInit(payment.getPaymentId());
                    case AWAITING_CONFIRM -> paymentService.processConfirm(payment.getPaymentId());
                    case INQUIRY_PENDING -> paymentService.processInquiry(payment.getPaymentId());
                    case REFUND_PENDING -> paymentService.processRefund(payment.getPaymentId());
                    default -> { }
                }
            } catch (Exception ex) { log.warn("Could not recover payment {}", payment.getPaymentId(), ex); }
        });
    }
}
