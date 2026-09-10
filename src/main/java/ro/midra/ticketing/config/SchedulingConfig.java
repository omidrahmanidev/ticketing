package ro.midra.ticketing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
