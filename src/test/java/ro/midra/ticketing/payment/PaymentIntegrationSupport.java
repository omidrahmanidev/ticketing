package ro.midra.ticketing.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.service.impl.OutboxDeliveryTransactions;
import ro.midra.ticketing.bootstrap.SeatSeeder;
import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.infrastructure.persistence.*;
import ro.midra.ticketing.payment.application.*;
import ro.midra.ticketing.payment.application.port.PaymentEventStore;
import ro.midra.ticketing.payment.application.port.PaymentGateway;
import ro.midra.ticketing.payment.application.port.PaymentIdentityStore;
import ro.midra.ticketing.payment.application.port.PaymentProjection;
import ro.midra.ticketing.payment.domain.PaymentState;
import ro.midra.ticketing.payment.infrastructure.eventstore.PaymentEventJpaRepository;
import ro.midra.ticketing.payment.infrastructure.gateway.DeterministicPaymentGateway;
import ro.midra.ticketing.payment.infrastructure.outbox.PaymentOutboxRouter;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public abstract class PaymentIntegrationSupport {
    @MockitoBean
    protected SeatSeeder seeder;
    @MockitoBean
    protected SeatLockPort locks;
    @MockitoBean
    protected PaymentGateway gateway;
    @MockitoBean
    protected Clock paymentClock;
    @Autowired
    protected StartPaymentHandler start;
    @Autowired
    protected PaymentEventStore events;
    @Autowired
    protected PaymentIdentityStore identities;
    @Autowired
    protected PaymentProjection projection;
    @Autowired
    protected PaymentReplayService replay;
    @Autowired
    protected PaymentProjectionRebuilder rebuilder;
    @Autowired
    protected PaymentStepTransactions steps;
    @Autowired
    protected PaymentCommandWorker worker;
    @Autowired
    protected PaymentOutboxRouter router;
    @Autowired
    protected OutboxDeliveryTransactions delivery;
    @Autowired
    protected OutboxEventJpaRepository outbox;
    @Autowired
    protected PaymentEventJpaRepository eventRows;
    @Autowired
    protected ro.midra.ticketing.domain.repository.ReservationRepository reservations;
    @Autowired
    protected ReservationJpaRepository reservationRows;
    @Autowired
    protected ro.midra.ticketing.domain.repository.SeatRepository seats;
    @Autowired
    protected SeatJpaRepository seatRows;
    @Autowired
    protected UserJpaRepository users;
    @Autowired
    protected EventJpaRepository shows;
    @Autowired
    protected PurchaseRequestJpaRepository purchases;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper mapper;
    protected final AtomicReference<Instant> instant = new AtomicReference<>();
    protected TransactionTemplate tx;
    protected DeterministicPaymentGateway provider;
    protected final List<String> gatewayKeys = new ArrayList<>();
    protected final AtomicBoolean crashAfterConfirm = new AtomicBoolean();
    protected Long userId, reservationId, seatId;

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of("payment_events", "payment_streams", "payments", "outbox_events", "reservation_seats",
                "purchase_request_seats", "purchase_requests", "seats", "reservations", "users", "events"))
            jdbc.update("delete from " + table);
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        instant.set(Instant.parse("2026-09-10T12:00:00Z"));
        when(paymentClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(paymentClock.instant()).thenAnswer(ignored -> instant.get());
        provider = new DeterministicPaymentGateway();
        gatewayKeys.clear();
        crashAfterConfirm.set(false);
        when(gateway.init(anyString(), any(), nullable(String.class))).thenAnswer(call -> {
            outsideTransaction(call.getArgument(0));
            return provider.init(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        });
        when(gateway.confirm(anyString(), nullable(String.class))).thenAnswer(call -> {
            outsideTransaction(call.getArgument(0));
            var result = provider.confirm(call.getArgument(0), call.getArgument(1));
            if (crashAfterConfirm.getAndSet(false)) throw new SimulatedProcessDeath();
            return result;
        });
        when(gateway.refund(anyString(), nullable(String.class), any())).thenAnswer(call -> {
            outsideTransaction(call.getArgument(0));
            return provider.refund(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        });
        when(gateway.inquiry(anyString(), nullable(String.class))).thenAnswer(call -> {
            outsideTransaction(call.getArgument(0));
            return provider.inquiry(call.getArgument(0), call.getArgument(1));
        });
        tx.executeWithoutResult(ignored -> {
            var now = LocalDateTime.now(paymentClock);
            var user = users.saveAndFlush(User.builder().phoneNumber(UUID.randomUUID().toString().substring(0, 16))
                    .createdAt(now).updatedAt(now).build());
            userId = user.getUserId();
            var show = shows.saveAndFlush(Event.builder().name("Test show").status(EventStatus.ON_SALE)
                    .startsAt(now.plusDays(1)).createdAt(now).updatedAt(now).build());
            var seat = seatRows.saveAndFlush(Seat.builder().event(show).seatNumber("A1").status(SeatStatus.HELD)
                    .createdAt(now).updatedAt(now).build());
            seatId = seat.getSeatId();
            var reservation = reservationRows.saveAndFlush(Reservation.builder().event(show).user(user).status(ReservationStatus.HELD)
                    .expiresAt(now.plusMinutes(10)).createdAt(now).updatedAt(now).build());
            reservation.getSeats().add(ReservationSeat.builder().id(new ReservationSeatId(reservation.getReservationId(), seatId))
                    .reservation(reservation).seat(seat).build());
            seat.setActiveReservation(reservation);
            seats.save(seat);
            reservationRows.saveAndFlush(reservation);
            reservationId = reservation.getReservationId();
            purchases.saveAndFlush(PurchaseRequest.builder().requestId("hold-owner").user(user).event(show).reservation(reservation)
                    .status(PurchaseRequestStatus.SUCCEEDED).createdAt(now).updatedAt(now).build());
        });
    }

    protected void outsideTransaction(String key) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("Gateway transaction boundary").isFalse();
        assertThat(jdbc.queryForObject("select count(*) from payment_events where event_type='PaymentProviderCallStarted'", Integer.class))
                .as("Dispatch must be committed before external I/O").isPositive();
        gatewayKeys.add(key);
    }

    protected PaymentResponse start(String scenario) {
        return start.startPayment(new ConfirmPaymentRequest(reservationId, userId, scenario));
    }

    protected PaymentState state(Long id) {
        return events.load(id).aggregate().state();
    }

    protected void advance(Duration amount) {
        instant.updateAndGet(time -> time.plus(amount));
    }

    protected void recover(Long id) {
        advance(Duration.ofMinutes(11));
        steps.recover(id);
    }

    protected List<OutboxEvent> pending() {
        return delivery.batch();
    }

    protected PaymentCommand command(OutboxEvent event) throws Exception {
        return mapper.readValue(event.getPayload(), PaymentCommand.class);
    }

    protected void deliver(OutboxEvent event) throws Exception {
        router.deliver(event);
        delivery.acknowledge(event.getId());
    }

    protected void drain() throws Exception {
        for (int batches = 0; batches < 30; batches++) {
            var pending = pending();
            if (pending.isEmpty()) return;
            for (var event : pending) deliver(event);
        }
        throw new AssertionError("Outbox did not settle");
    }

    protected static class SimulatedProcessDeath extends Error {
    }
}
