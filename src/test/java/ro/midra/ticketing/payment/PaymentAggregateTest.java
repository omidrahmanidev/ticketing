package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ro.midra.ticketing.payment.domain.*;
import ro.midra.ticketing.payment.domain.PaymentEvent.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static ro.midra.ticketing.payment.domain.PaymentStatus.*;
import static ro.midra.ticketing.payment.domain.PaymentOperation.*;

class PaymentAggregateTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 12, 0);
    private PaymentAggregate initiated() {
        return PaymentAggregate.start(1L, 2L, 3L, new BigDecimal("12.50"), "payment-stable", NOW);
    }
    private PaymentAggregate confirming() {
        var payment = initiated();
        payment.initRequested("init", NOW);
        payment.initSucceeded("provider-1", NOW);
        payment.confirmRequested("confirm", NOW);
        return payment;
    }
    @Test void successfulLifecycleRaisesFactsAndUsesStableKeys() {
        var payment = confirming();
        payment.confirmSucceeded("provider-1", NOW);
        assertThat(payment.state().status()).isEqualTo(CONFIRMED);
        assertThat(payment.state().amount()).isEqualByComparingTo("12.50");
        assertThat(payment.idempotencyKey(CONFIRM)).isEqualTo("payment-stable-CONFIRM");
        assertThat(payment.getUncommittedEvents()).hasSize(5);
        assertThat(payment.committedVersion()).isZero();
        var historical = payment.getUncommittedEvents();
        payment.markEventsCommitted();
        assertThat(payment.committedVersion()).isEqualTo(5);
        assertThat(PaymentAggregate.rehydrate(historical).state()).isEqualTo(payment.state());
    }
    @Test void replayKnownHistoryRestoresEveryRecoveryFieldWithoutRaisingEvents() {
        LocalDateTime due = NOW.plusMinutes(4);
        var payment = PaymentAggregate.rehydrate(List.of(
                new PaymentInitiated(1L, 2L, 3L, new BigDecimal("12.50"), "payment-stable", NOW),
                new PaymentInitRequested("init", NOW),
                new PaymentInitSucceeded("provider-1", NOW),
                new PaymentConfirmRequested("confirm", NOW),
                new PaymentConfirmTimedOut("socket timeout", NOW),
                new PaymentRetryScheduled(2, due, "unknown outcome", NOW)));
        assertThat(payment.state()).isEqualTo(new PaymentState(1L, 2L, 3L, new BigDecimal("12.50"),
                INQUIRY_PENDING, "payment-stable", "provider-1", CONFIRM, 2, due,
                "unknown outcome", 6L, NOW, NOW, null, null, false));
        assertThat(payment.getUncommittedEvents()).isEmpty();
        assertThat(payment.committedVersion()).isEqualTo(6);
    }
    @Test void initFailureIsTerminal() {
        var payment = initiated();
        payment.initRequested("init", NOW);
        payment.initFailed("INSUFFICIENT_FUNDS", NOW);
        assertThat(payment.state().status()).isEqualTo(INIT_FAILED);
        assertThat(payment.state().lastError()).isEqualTo("INSUFFICIENT_FUNDS");
    }
    @ParameterizedTest @EnumSource(value = PaymentOperation.class, names = {"INIT", "CONFIRM", "REFUND"})
    void inquiryResolvesEveryUncertainOperation(PaymentOperation operation) {
        var payment = operation == INIT ? initiated() : confirming();
        switch (operation) {
            case INIT -> { payment.initRequested("init", NOW); payment.initTimedOut("timeout", "provider-1", NOW); }
            case CONFIRM -> payment.confirmTimedOut("timeout", NOW);
            case REFUND -> {
                payment.confirmFailed("declined", NOW); payment.refundRequested("refund", NOW);
                payment.refundTimedOut("timeout", NOW);
            }
            default -> throw new AssertionError();
        }
        assertThat(payment.state().status()).isEqualTo(INQUIRY_PENDING);
        assertThat(payment.state().targetOperation()).isEqualTo(operation);
        payment.inquiryRequested("inquiry", NOW);
        payment.inquiryResolved(true, "provider-1", "resolved", NOW);
        assertThat(payment.state().status()).isEqualTo(operation == INIT ? AWAITING_CONFIRM : operation == CONFIRM ? CONFIRMED : REFUNDED);
        assertThat(payment.state().targetOperation()).isNull();
        assertThat(payment.state().lastError()).isNull();
        assertThat(payment.state().nextRetryAt()).isNull();
        assertThat(PaymentAggregate.rehydrate(payment.getUncommittedEvents()).state()).isEqualTo(payment.state());
    }
    @Test void failedConfirmInquiryRequiresCompensation() {
        var payment = confirming();
        payment.confirmTimedOut("unknown", NOW);
        payment.inquiryRequested("inquiry", NOW);
        payment.inquiryResolved(false, "provider-1", "declined", NOW);
        assertThat(payment.state().status()).isEqualTo(REFUND_PENDING);
        payment.refundRequested("refund", NOW);
        payment.refundSucceeded("provider-1", NOW);
        assertThat(payment.state().status()).isEqualTo(REFUNDED);
    }
    @Test void retryAndStuckStateReplayExactly() {
        var payment = confirming();
        payment.confirmFailed("declined", NOW);
        payment.refundRequested("refund", NOW);
        payment.refundFailed("temporary", NOW);
        payment.retryScheduled(1, NOW.plusMinutes(1), "retry", NOW);
        assertThat(payment.state().retryCount()).isEqualTo(1);
        assertThat(payment.state().nextRetryAt()).isEqualTo(NOW.plusMinutes(1));
        payment.markStuck("customer charged; manual refund required", NOW);
        assertThat(payment.state().status()).isEqualTo(STUCK);
        assertThat(payment.state().nextRetryAt()).isNull();
        assertThat(PaymentAggregate.rehydrate(payment.getUncommittedEvents()).state()).isEqualTo(payment.state());
    }
    @Test void unresolvedDispatchIsReplayableAndRejectsDuplicateDispatch() {
        var payment = confirming();
        payment.providerCallStarted("confirm", NOW.plusMinutes(1), NOW);
        assertThat(payment.state().dispatched()).isTrue();
        assertThat(payment.state().nextRetryAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(PaymentAggregate.rehydrate(payment.getUncommittedEvents()).state()).isEqualTo(payment.state());
        assertThatThrownBy(() -> payment.providerCallStarted("confirm", NOW.plusMinutes(1), NOW)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsInvalidTransitionsAndIdentity() {
        var payment = initiated();
        assertThatThrownBy(() -> payment.confirmRequested("confirm", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.confirmSucceeded("ref", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.refundRequested("refund", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.inquiryRequested("inquiry", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.retryScheduled(1, NOW.plusMinutes(1), "why", NOW)).isInstanceOf(IllegalStateException.class);
        payment.initRequested("init", NOW);
        assertThatThrownBy(() -> payment.initRequested("duplicate", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.initSucceeded(null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentAggregate.start(1L, 2L, 3L, BigDecimal.ZERO, "key", NOW)).isInstanceOf(IllegalStateException.class);
    }
    @Test void terminalPaymentRejectsFurtherCommands() {
        var payment = confirming();
        payment.confirmSucceeded("ref", NOW);
        assertThatThrownBy(() -> payment.confirmFailed("failure", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.refundRequested("refund", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.markStuck("failure", NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(payment.accepts("confirm", CONFIRM)).isFalse();
    }
}
