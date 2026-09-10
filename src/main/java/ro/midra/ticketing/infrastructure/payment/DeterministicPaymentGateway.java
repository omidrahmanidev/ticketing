package ro.midra.ticketing.infrastructure.payment;

import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.service.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeterministicPaymentGateway implements PaymentGateway {

    private final Map<String, GatewayResult> results = new ConcurrentHashMap<>();
    private final Map<String, String> scenariosByRoot = new ConcurrentHashMap<>();

    @Override
    public GatewayResult init(String key, BigDecimal amount, String scenarioHint) {
        return results.computeIfAbsent(key, ignored -> {
            String scenario = normalize(scenarioHint);
            scenariosByRoot.put(root(key), scenario);
            return resultFor(scenario, "ref-" + Math.abs(key.hashCode()), true);
        });
    }

    @Override
    public GatewayResult confirm(String key, String providerReference) {
        return results.computeIfAbsent(key, ignored -> resultFor(scenariosByRoot.getOrDefault(root(key), "SUCCESS"), providerReference, false));
    }

    @Override
    public GatewayResult refund(String key, String providerReference, BigDecimal amount) {
        return results.computeIfAbsent(key, ignored -> {
            String scenario = scenariosByRoot.getOrDefault(root(key), "SUCCESS");
            Outcome outcome = "REFUND_FAILURE".equals(scenario) ? Outcome.REFUND_FAILURE : Outcome.REFUND_SUCCESS;
            return new GatewayResult(outcome, providerReference, scenario);
        });
    }

    @Override
    public GatewayResult inquiry(String key, String providerReference) {
        return results.getOrDefault(key, new GatewayResult(Outcome.UNKNOWN, providerReference, "UNKNOWN"));
    }

    private GatewayResult resultFor(String scenario, String reference, boolean init) {
        Outcome outcome = switch (scenario) {
            case "INSUFFICIENT_FUNDS" -> Outcome.INSUFFICIENT_FUNDS;
            case "TEMPORARY_FAILURE" -> Outcome.TEMPORARY_FAILURE;
            case "TIMEOUT" -> Outcome.TIMEOUT;
            case "CONFIRMATION_FAILED", "REFUND_FAILURE" -> init ? Outcome.SUCCESS : Outcome.CONFIRMATION_FAILED;
            default -> Outcome.SUCCESS;
        };
        return new GatewayResult(outcome, reference, scenario);
    }

    private String normalize(String value) {
        return value == null ? "SUCCESS" : value.trim().toUpperCase();
    }

    private String root(String key) {
        return key.substring(0, key.lastIndexOf('-'));
    }
}
