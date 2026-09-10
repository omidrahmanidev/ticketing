package ro.midra.ticketing.payment.infrastructure.eventstore;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Refuse to process an unmigrated audit-log database with the event-sourced workers. */
@Component
@RequiredArgsConstructor
public class PaymentSchemaGuard implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;
    public void afterPropertiesSet() {
        var legacy = jdbc.queryForList("select id from payment_events where event_type = 'PAYMENT_INITIATED' limit 1");
        if (!legacy.isEmpty()) throw new IllegalStateException(
                "Legacy payment audit data requires db/manual/payment-event-sourcing-cutover.sql; see docs/payment-architecture.md");
    }
}
