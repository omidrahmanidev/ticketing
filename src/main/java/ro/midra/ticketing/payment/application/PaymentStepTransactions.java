package ro.midra.ticketing.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import ro.midra.ticketing.payment.application.port.*;
import ro.midra.ticketing.payment.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentStepTransactions {
    private final PaymentEventStore events;
    private final PaymentCommitter committer;
    private final PaymentSaga saga;
    private final Clock paymentClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentState prepare(PaymentCommand command) {
        var payment = events.load(command.paymentId()).aggregate();
        if (!payment.accepts(command.requestId(), command.operation()) || payment.state().dispatched()) return null;
        var now = LocalDateTime.now(paymentClock);
        payment.providerCallStarted(command.requestId(), now.plusSeconds(60), now);
        committer.commit(payment);
        return payment.state();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void complete(PaymentCommand command, PaymentGateway.GatewayResult result) {
        var payment = events.load(command.paymentId()).aggregate();
        if (!payment.accepts(command.requestId(), command.operation()) || !payment.state().dispatched()) return;
        saga.onResult(payment, command, result, LocalDateTime.now(paymentClock));
        committer.commit(payment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recover(Long paymentId) {
        var payment = events.load(paymentId).aggregate();
        var state = payment.state();
        var now = LocalDateTime.now(paymentClock);
        if (payment.terminal() || state.nextRetryAt() == null || state.nextRetryAt().isAfter(now)) return;
        log.info("Recovering paymentId={} reservationId={} operation={} retry={}", paymentId,
                state.reservationId(), state.targetOperation(), state.retryCount());
        if (state.dispatched()) {
            // Missing result is UNKNOWN, including a process death after the provider charged.
            saga.unresolved(payment, state.requestedOperation(), "Provider call has no committed result", state.providerReference(), now);
        } else if (state.requestId() == null) {
            var operation = state.status() == PaymentStatus.REFUND_PENDING ? PaymentOperation.REFUND : PaymentOperation.INQUIRY;
            saga.request(payment, operation, null, now);
        }
        committer.commit(payment);
    }
}
