package ro.midra.ticketing.payment.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.payment.application.PaymentProjectionRebuilder;

@Component
@Order(0)
@ConditionalOnProperty(name = "payment.projections.rebuild-on-startup", havingValue = "true")
@RequiredArgsConstructor
public class PaymentProjectionStartup implements ApplicationRunner {
    private final PaymentProjectionRebuilder rebuilder;
    public void run(ApplicationArguments args) { rebuilder.rebuildAll(); }
}
