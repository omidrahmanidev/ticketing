package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.exception.InvalidReservationStateException;
import ro.midra.ticketing.domain.ReservationStatus;
import ro.midra.ticketing.domain.SeatStatus;
import ro.midra.ticketing.payment.domain.PaymentState;
import ro.midra.ticketing.payment.domain.PaymentStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ro.midra.ticketing.payment.domain.PaymentStatus.*;

class PaymentRetryIntegrationTest extends PaymentIntegrationSupport {
    @Autowired WebApplicationContext web;

    @Test void repeatedFailuresKeepOriginalHoldAndEachAttemptHistoryUntilSuccess() throws Exception {
        var expiresAt = reservations.findById(reservationId).orElseThrow().getExpiresAt();
        var seatUpdatedAt = seats.findById(seatId).orElseThrow().getUpdatedAt();
        var attempts = new ArrayList<PaymentState>();
        for (String scenario : List.of("INSUFFICIENT_FUNDS", "CONFIRMATION_FAILED", "TEMPORARY_FAILURE")) {
            advance(Duration.ofMinutes(1));
            var payment = start(scenario); drain();
            var failed = state(payment.paymentId());
            assertThat(failed.status()).isEqualTo(scenario.equals("CONFIRMATION_FAILED") ? REFUNDED : INIT_FAILED);
            assertThat(attempts).extracting(PaymentState::paymentId).doesNotContain(failed.paymentId());
            assertThat(attempts).extracting(PaymentState::operationId).doesNotContain(failed.operationId());
            attempts.add(failed);
            assertThat(identities.findLatestPaymentId(reservationId)).contains(failed.paymentId());
            var reservation = reservations.findById(reservationId).orElseThrow();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
            assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
            var seat = seats.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getActiveReservation().getReservationId()).isEqualTo(reservationId);
            assertThat(seat.getUpdatedAt()).isEqualTo(seatUpdatedAt);
            assertThat(outbox.findAll()).extracting("aggregateType").doesNotContain("Reservation", "SeatLockRelease");
            verifyNoInteractions(locks);
        }
        var success = start("SUCCESS"); drain();
        assertThat(state(success.paymentId()).status()).isEqualTo(CONFIRMED);
        for (var attempt : attempts) {
            assertThat(state(attempt.paymentId())).isEqualTo(attempt);
            assertThat(projection.find(attempt.paymentId())).contains(attempt);
        }
        attempts.add(state(success.paymentId()));
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactlyElementsOf(
                attempts.stream().map(PaymentState::paymentId).toList());
        assertThat(start("INSUFFICIENT_FUNDS").paymentId()).isEqualTo(success.paymentId());
        assertThat(identities.findAllPaymentIds(reservationId)).hasSize(4);
        assertThat(reservations.findById(reservationId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(reservations.findById(reservationId).orElseThrow().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.PAID);
        assertThat(outbox.findAll()).extracting("eventType").containsOnlyOnce("SEAT_CONFIRMED", "RELEASE_SEAT_LOCKS")
                .doesNotContain("RESERVATION_CANCELLED");
        verify(locks).unlockOwned(List.of(seatId), "hold-owner");
    }

    @ParameterizedTest @CsvSource({"INSUFFICIENT_FUNDS, 600", "INSUFFICIENT_FUNDS, 601",
            "CONFIRMATION_FAILED, 600", "CONFIRMATION_FAILED, 601"})
    void retryAtOrAfterOriginalExpiryIsRejected(String scenario, long elapsedSeconds) throws Exception {
        var payment = start(scenario); drain();
        var before = state(payment.paymentId());
        advance(Duration.ofSeconds(elapsedSeconds));
        assertThatThrownBy(() -> start("SUCCESS")).isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("expired");
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactly(payment.paymentId());
        assertThat(state(payment.paymentId())).isEqualTo(before);
        assertThat(pending()).isEmpty();
        verifyNoInteractions(locks);
    }

    @Test void failureDeliveredAfterHoldExpiryDoesNotRenewTheHold() throws Exception {
        var payment = start("INSUFFICIENT_FUNDS");
        deliver(pending().getFirst());
        var expiresAt = reservations.findById(reservationId).orElseThrow().getExpiresAt();
        advance(Duration.ofMinutes(11));
        drain();
        var reservation = reservations.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
        assertThatThrownBy(() -> start("SUCCESS")).isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("expired");
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactly(payment.paymentId());
    }

    @ParameterizedTest @CsvSource({"SUCCESS, 0, INIT_PENDING", "SUCCESS, 1, AWAITING_CONFIRM",
            "TIMEOUT, 1, INQUIRY_PENDING", "CONFIRMATION_FAILED, 2, REFUND_PENDING", "SUCCESS, 2, CONFIRMED"})
    void inFlightAndConfirmedAttemptsAreReturnedWithoutAllocating(String scenario, int deliveries, PaymentStatus status) throws Exception {
        var failed = start("INSUFFICIENT_FUNDS"); drain();
        var payment = start(scenario);
        for (int i = 0; i < deliveries; i++) deliver(pending().getFirst());
        var before = state(payment.paymentId());
        assertThat(before.status()).isEqualTo(status);
        var repeated = start("INSUFFICIENT_FUNDS");
        assertThat(repeated.paymentId()).isEqualTo(payment.paymentId());
        assertThat(repeated.status()).isEqualTo(status);
        assertThat(state(payment.paymentId())).isEqualTo(before);
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactly(failed.paymentId(), payment.paymentId());
    }

    @ParameterizedTest @ValueSource(strings = {"INSUFFICIENT_FUNDS", "CONFIRMATION_FAILED"})
    void simultaneousRetriesAllocateOnlyOneNewAttempt(String scenario) throws Exception {
        var failed = start(scenario); drain();
        var ready = new CyclicBarrier(2);
        Long retriedId;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> retry = () -> { ready.await(5, TimeUnit.SECONDS); return start("SUCCESS").paymentId(); };
            var first = executor.submit(retry);
            var second = executor.submit(retry);
            retriedId = first.get(15, TimeUnit.SECONDS);
            assertThat(retriedId).isEqualTo(second.get(15, TimeUnit.SECONDS)).isGreaterThan(failed.paymentId());
        }
        assertThat(identities.findAllPaymentIds(reservationId)).containsExactly(failed.paymentId(), retriedId);
        assertThat(eventRows.findByPaymentIdOrderBySequenceNumberAsc(retriedId)).hasSize(2);
        assertThat(pending()).hasSize(1);
    }

    @Test void historyEndpointReturnsAllAttemptsInAllocationOrderWithoutProjections() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        mvc.perform(get("/api/reservations/{reservationId}/payments", reservationId))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        var expected = new ArrayList<PaymentResponse>();
        for (String scenario : List.of("INSUFFICIENT_FUNDS", "CONFIRMATION_FAILED", "SUCCESS")) {
            var payment = start(scenario); drain();
            expected.add(replay.replay(payment.paymentId()));
        }
        var eventCount = eventRows.count();
        jdbc.update("delete from payments");
        mvc.perform(get("/api/reservations/{reservationId}/payments", reservationId))
                .andExpect(status().isOk()).andExpect(content().json(mapper.writeValueAsString(expected)))
                .andExpect(jsonPath("$[0].paymentId").value(expected.get(0).paymentId()))
                .andExpect(jsonPath("$[1].paymentId").value(expected.get(1).paymentId()))
                .andExpect(jsonPath("$[2].paymentId").value(expected.get(2).paymentId()));
        assertThat(eventRows.count()).isEqualTo(eventCount);
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();
    }
}
