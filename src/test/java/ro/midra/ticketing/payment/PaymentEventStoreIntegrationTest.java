package ro.midra.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ro.midra.ticketing.application.exception.ConcurrentPaymentModificationException;
import ro.midra.ticketing.application.service.OutboxEventWriter;
import ro.midra.ticketing.payment.application.PaymentCommitter;
import ro.midra.ticketing.payment.domain.PaymentEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class PaymentEventStoreIntegrationTest extends PaymentIntegrationSupport {
    @Autowired PaymentCommitter committer;
    @Autowired OutboxEventWriter writer;

    @Test void projectionCanBeDeletedAndRebuiltWithoutChangingIdentityOrState() throws Exception {
        var payment = start("CONFIRM_TIMEOUT_THEN_SUCCESS"); drain();
        var expected = state(payment.paymentId());
        jdbc.update("delete from payments");
        assertThat(projection.find(payment.paymentId())).isEmpty();
        assertThat(replay.replay(payment.paymentId()).status()).isEqualTo(expected.status());
        assertThat(start("SUCCESS").paymentId()).isEqualTo(payment.paymentId());
        assertThat(rebuilder.rebuildAll()).isEqualTo(1);
        assertThat(projection.find(payment.paymentId()).orElseThrow()).isEqualTo(expected);
        assertThat(events.load(payment.paymentId()).aggregate().getUncommittedEvents()).isEmpty();
    }
    @Test void staleRebuildCannotOverwriteNewerProjection() throws Exception {
        var payment = start("SUCCESS");
        var oldStream = events.load(payment.paymentId());
        drain();
        tx.executeWithoutResult(ignored -> projection.project(oldStream));
        assertThat(projection.find(payment.paymentId()).orElseThrow()).isEqualTo(state(payment.paymentId()));
    }
    @Test void simultaneousStartsReturnOnePaymentSerializedByReservationLock() throws Exception {
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> startSameReservation = () -> { ready.await(5, TimeUnit.SECONDS); return start("SUCCESS").paymentId(); };
            var first = executor.submit(startSameReservation);
            var second = executor.submit(startSameReservation);
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from payment_streams", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_events", Integer.class)).isEqualTo(2);
        assertThat(outbox.findAll()).hasSize(1);
    }
    @Test void databaseArbitratesTwoAppendsWithSameExpectedVersion() throws Exception {
        var payment = start("SUCCESS");
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> append = () -> {
                try {
                    tx.executeWithoutResult(ignored -> {
                        var aggregate = events.load(payment.paymentId()).aggregate();
                        try { ready.await(5, TimeUnit.SECONDS); }
                        catch (Exception ex) { throw new RuntimeException(ex); }
                        aggregate.initSucceeded("provider-1", LocalDateTime.now(paymentClock));
                        events.append(payment.paymentId(), aggregate.committedVersion(), aggregate.getUncommittedEvents());
                    });
                    return "committed";
                } catch (ConcurrentPaymentModificationException expected) { return "conflict"; }
            };
            var first = executor.submit(append);
            var second = executor.submit(append);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("committed", "conflict");
        }
        assertThat(eventRows.findByPaymentIdOrderBySequenceNumberAsc(payment.paymentId())).hasSize(3);
    }
    @Test void optimisticConflictRejectsStaleAndFutureVersions() {
        var payment = start("SUCCESS");
        var event = new PaymentEvent.PaymentInitSucceeded("ref", LocalDateTime.now(paymentClock));
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> events.append(payment.paymentId(), 1, List.of(event))))
                .isInstanceOf(ConcurrentPaymentModificationException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> events.append(payment.paymentId(), 100, List.of(event))))
                .isInstanceOf(ConcurrentPaymentModificationException.class);
    }
    @Test void failedTransactionRollsBackEventsProjectionAndOutboxTogether() {
        var payment = start("SUCCESS");
        var before = state(payment.paymentId());
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            var aggregate = events.load(payment.paymentId()).aggregate();
            aggregate.initSucceeded("ref", LocalDateTime.now(paymentClock));
            committer.commit(aggregate);
            writer.write("PaymentCommand", payment.paymentId().toString(), "TEST", "payload");
            throw new IllegalStateException("transaction failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state(payment.paymentId())).isEqualTo(before);
        assertThat(projection.find(payment.paymentId()).orElseThrow()).isEqualTo(before);
        assertThat(outbox.findAll()).hasSize(1);
    }
    @Test void staleResultCannotOverrideRecoveryThatAlreadyAdvanced() throws Exception {
        var payment = start("SUCCESS"); deliver(pending().getFirst());
        var confirm = command(pending().getFirst());
        steps.prepare(confirm);
        recover(payment.paymentId());
        steps.complete(confirm, new ro.midra.ticketing.payment.application.port.PaymentGateway.GatewayResult(
                ro.midra.ticketing.payment.application.port.PaymentGateway.Outcome.SUCCESS, "late-reference", "late"));
        assertThat(state(payment.paymentId()).status()).isEqualTo(ro.midra.ticketing.payment.domain.PaymentStatus.INQUIRY_PENDING);
        assertThat(state(payment.paymentId()).providerReference()).isNotEqualTo("late-reference");
    }
}
