package ro.midra.ticketing.payment.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.time.Clock;

@Configuration
public class PaymentConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    public Clock paymentClock() { return Clock.tickMillis(java.time.ZoneId.systemDefault()); }
}
