package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ro.midra.ticketing.application.dto.PaymentDto.*;
import ro.midra.ticketing.application.exception.InvalidReservationStateException;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.payment.application.*;
import ro.midra.ticketing.payment.application.port.PaymentGateway;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static ro.midra.ticketing.payment.domain.PaymentStatus.*;

class PaymentSagaIntegrationTest extends PaymentIntegrationSupport {
    @Test void successfulFlowUsesSeparateDurableStepsAndReservationIntegration() throws Exception {
        var payment = start("SUCCESS");
        assertThat(payment.status()).isEqualTo(INIT_PENDING);
        verifyNoInteractions(gateway);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(eventRows.findByPaymentIdOrderBySequenceNumberAsc(payment.paymentId())).hasSize(2);
        deliver(pending().getFirst());
        assertThat(state(payment.paymentId()).status()).isEqualTo(AWAITING_CONFIRM);
        verify(gateway, never()).confirm(anyString(), anyString());
        assertThat(pending().getFirst().getEventType()).isEqualTo("CONFIRM_PAYMENT");
        deliver(pending().getFirst());
        assertThat(state(payment.paymentId()).status()).isEqualTo(CONFIRMED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        drain();
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.PAID);
        verify(locks).unlockOwned(java.util.List.of(seatId), "hold-owner");
        assertThat(projection.find(payment.paymentId()).orElseThrow()).isEqualTo(state(payment.paymentId()));
    }
    @ParameterizedTest @ValueSource(strings = {"INSUFFICIENT_FUNDS", "TEMPORARY_FAILURE"})
    void initFailureRestoresReservationHoldAndKeepsSeats(String scenario) throws Exception {
        var payment = start(scenario); drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(INIT_FAILED);
        assertThat(state(payment.paymentId()).lastError()).contains(scenario);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        verifyNoInteractions(locks);
        verify(gateway, never()).confirm(anyString(), anyString());
    }
    @ParameterizedTest @ValueSource(strings = {"INIT_TIMEOUT_THEN_SUCCESS", "CONFIRM_TIMEOUT_THEN_SUCCESS"})
    void timedOutOperationIsResolvedByInquiry(String scenario) throws Exception {
        var payment = start(scenario); drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(INQUIRY_PENDING);
        assertThat(state(payment.paymentId()).nextRetryAt()).isNotNull();
        assertThat(state(payment.paymentId()).providerReference()).isNotBlank();
        recover(payment.paymentId()); drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(CONFIRMED);
        verify(gateway).inquiry(anyString(), nullable(String.class));
        assertThat(state(payment.paymentId()).targetOperation()).isNull();
        assertThat(replay.replay(payment.paymentId()).status()).isEqualTo(CONFIRMED);
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIRMATION_FAILED", "CONFIRM_TIMEOUT_THEN_FAILURE"})
    void confirmFailureSchedulesRefundCompensation(String scenario) throws Exception {
        var payment = start(scenario); drain();
        if (state(payment.paymentId()).status() == INQUIRY_PENDING) { recover(payment.paymentId()); drain(); }
        assertThat(state(payment.paymentId()).status()).isEqualTo(REFUNDED);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        verifyNoInteractions(locks);
        verify(gateway).refund(anyString(), anyString(), any());
        assertThat(eventRows.findByPaymentIdOrderBySequenceNumberAsc(payment.paymentId())).extracting("eventType")
                .contains("PaymentConfirmFailed", "PaymentRefundRequested", "PaymentRefunded");
    }
    @Test void refundTimeoutResolvesThroughInquiryIncludingRefundSuccessOutcome() throws Exception {
        var payment = start("REFUND_TIMEOUT_THEN_SUCCESS"); drain();
        assertThat(state(payment.paymentId()).targetOperation()).isEqualTo(ro.midra.ticketing.payment.domain.PaymentOperation.REFUND);
        recover(payment.paymentId()); drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(REFUNDED);
        verify(gateway).inquiry(endsWith("-REFUND"), anyString());
    }
    @Test void boundedRefundRetriesUseSameKeyAndEndInManualRefund() throws Exception {
        var payment = start("REFUND_FAILURE"); drain();
        assertThat(state(payment.paymentId()).retryCount()).isEqualTo(1);
        assertThat(state(payment.paymentId()).nextRetryAt()).isEqualTo(java.time.LocalDateTime.now(paymentClock).plusSeconds(60));
        for (int i = 0; i < 6; i++) { recover(payment.paymentId()); drain(); }
        assertThat(state(payment.paymentId()).status()).isEqualTo(STUCK);
        assertThat(state(payment.paymentId()).lastError()).contains("manual refund required");
        assertThat(state(payment.paymentId()).nextRetryAt()).isNull();
        assertThat(gatewayKeys.stream().filter(key -> key.endsWith("-REFUND")).distinct()).hasSize(1);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }
    @Test void unknownInquiryExhaustsRetriesWithoutReleasingSeats() throws Exception {
        var payment = start("TIMEOUT"); drain();
        for (int i = 0; i < 7; i++) { recover(payment.paymentId()); drain(); }
        assertThat(state(payment.paymentId()).status()).isEqualTo(STUCK);
        assertThat(state(payment.paymentId()).lastError()).contains("manual inquiry");
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(start("SUCCESS").paymentId()).isEqualTo(payment.paymentId());
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactly(payment.paymentId());
    }
    @Test void duplicateStartAndOutboxDeliveryAreIdempotent() throws Exception {
        var payment = start("SUCCESS");
        assertThat(start("INSUFFICIENT_FUNDS").paymentId()).isEqualTo(payment.paymentId());
        var init = pending().getFirst(); deliver(init); deliver(init);
        var confirm = pending().getFirst(); deliver(confirm); deliver(confirm);
        var integration = pending().getFirst(); deliver(integration); deliver(integration);
        drain();
        verify(gateway, times(1)).init(anyString(), any(), nullable(String.class));
        verify(gateway, times(1)).confirm(anyString(), anyString());
        assertThat(jdbc.queryForObject("select count(*) from payment_streams", Integer.class)).isEqualTo(1);
        assertThat(outbox.findAll().stream().filter(e -> e.getEventType().equals("SEAT_CONFIRMED"))).hasSize(1);
    }
    @Test void confirmedAtProviderButCrashBeforeResultRecoversByInquiry() throws Exception {
        var payment = start("SUCCESS"); deliver(pending().getFirst());
        var confirm = pending().getFirst();
        crashAfterConfirm.set(true);
        assertThatThrownBy(() -> deliver(confirm)).isInstanceOf(SimulatedProcessDeath.class);
        assertThat(state(payment.paymentId()).dispatched()).isTrue();
        assertThat(state(payment.paymentId()).status()).isEqualTo(AWAITING_CONFIRM);
        assertThat(eventRows.findByPaymentIdOrderBySequenceNumberAsc(payment.paymentId())).extracting("eventType")
                .contains("PaymentConfirmRequested").doesNotContain("PaymentConfirmed");
        // Redelivery after restart cannot blindly send CONFIRM again.
        deliver(confirm);
        recover(payment.paymentId());
        assertThat(state(payment.paymentId()).status()).isEqualTo(INQUIRY_PENDING);
        recover(payment.paymentId()); drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(CONFIRMED);
        verify(gateway, times(1)).confirm(anyString(), anyString());
        verify(gateway).inquiry(endsWith("-CONFIRM"), anyString());
    }
    @Test void durableConfirmIntentCanBeConsumedByFreshWorkerAfterInitCommit() throws Exception {
        var payment = start("SUCCESS"); deliver(pending().getFirst());
        var confirm = pending().getFirst();
        new PaymentCommandWorker(steps, gateway).execute(command(confirm));
        assertThat(state(payment.paymentId()).status()).isEqualTo(CONFIRMED);
    }
    @Test void expiredAndNonHeldReservationsAreRejected() {
        tx.executeWithoutResult(ignored -> { var r = reservations.findById(reservationId).orElseThrow(); r.setExpiresAt(java.time.LocalDateTime.now(paymentClock)); reservations.save(r); });
        assertThatThrownBy(() -> start("SUCCESS")).isInstanceOf(InvalidReservationStateException.class).hasMessageContaining("expired");
        tx.executeWithoutResult(ignored -> { var r = reservations.findById(reservationId).orElseThrow(); r.setExpiresAt(java.time.LocalDateTime.now(paymentClock).plusMinutes(5)); r.setStatus(ReservationStatus.PAID); reservations.save(r); });
        assertThatThrownBy(() -> start("SUCCESS")).isInstanceOf(InvalidReservationStateException.class).hasMessageContaining("HELD");
        verifyNoInteractions(gateway);
    }
    @Test void ownershipCheckedAlsoForIdempotentRepeat() {
        Long stranger = tx.execute(ignored -> users.saveAndFlush(User.builder().phoneNumber("stranger").createdAt(java.time.LocalDateTime.now(paymentClock)).updatedAt(java.time.LocalDateTime.now(paymentClock)).build()).getUserId());
        var wrongUser = new ConfirmPaymentRequest(reservationId, stranger, null);
        assertThatThrownBy(() -> start.startPayment(wrongUser)).isInstanceOf(InvalidReservationStateException.class).hasMessageContaining("belong");
        start("SUCCESS");
        assertThatThrownBy(() -> start.startPayment(wrongUser)).isInstanceOf(InvalidReservationStateException.class).hasMessageContaining("belong");
    }
    @Test void gatewayExceptionsAreUnknownAndGatewayCannotBeCalledFromTransaction() throws Exception {
        var payment = start("SUCCESS");
        var command = command(pending().getFirst());
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> worker.execute(command)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        doThrow(new IllegalStateException("network")).when(gateway).init(anyString(), any(), nullable(String.class));
        drain();
        assertThat(state(payment.paymentId()).status()).isEqualTo(INQUIRY_PENDING);
    }
    @Test void missingReservationAndUserAreRejectedWithoutCreatingAStream() {
        assertThatThrownBy(() -> start.startPayment(new ConfirmPaymentRequest(Long.MAX_VALUE, userId, null)))
                .isInstanceOf(ro.midra.ticketing.application.exception.NotFoundException.class);
        assertThatThrownBy(() -> start.startPayment(new ConfirmPaymentRequest(reservationId, Long.MAX_VALUE, null)))
                .isInstanceOf(ro.midra.ticketing.application.exception.NotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from payment_events", Integer.class)).isZero();
    }
    @Test void recoveryDoesNotActBeforePersistedDeadline() throws Exception {
        var payment = start("INIT_TIMEOUT_THEN_SUCCESS"); drain();
        var before = state(payment.paymentId());
        steps.recover(payment.paymentId());
        assertThat(state(payment.paymentId())).isEqualTo(before);
        assertThat(pending()).isEmpty();
    }
    @Test void failedRedisReleaseRemainsRetryableAfterReservationCommits() throws Exception {
        var payment = start("SUCCESS");
        deliver(pending().getFirst()); deliver(pending().getFirst()); deliver(pending().getFirst());
        var release = pending().stream().filter(e -> e.getAggregateType().equals("SeatLockRelease")).findFirst().orElseThrow();
        doThrow(new IllegalStateException("Redis unavailable")).when(locks).unlockOwned(anyList(), anyString());
        assertThatThrownBy(() -> deliver(release)).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(release.getId()).orElseThrow().isPublished()).isFalse();
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        doNothing().when(locks).unlockOwned(anyList(), anyString());
        deliver(release);
        assertThat(outbox.findById(release.getId()).orElseThrow().isPublished()).isTrue();
    }

}
