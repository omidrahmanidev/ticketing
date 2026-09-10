package ro.midra.ticketing.payment.infrastructure.gateway;

import org.springframework.stereotype.Component;
import ro.midra.ticketing.payment.application.port.PaymentGateway;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory provider simulator. Production providers must retain their own idempotency ledger. */
@Component
public class DeterministicPaymentGateway implements PaymentGateway {
    private final Map<String, GatewayResult> responses = new ConcurrentHashMap<>();
    private final Map<String, GatewayResult> providerOutcomes = new ConcurrentHashMap<>();
    private final Map<String, String> scenarios = new ConcurrentHashMap<>();

    public GatewayResult init(String key, BigDecimal amount, String hint) {
        return responses.computeIfAbsent(key, ignored -> {
            String scenario = hint == null ? "SUCCESS" : hint.trim().toUpperCase(Locale.ROOT);
            scenarios.put(root(key), scenario);
            String reference = "ref-" + Integer.toUnsignedString(key.hashCode());
            Outcome outcome = switch (scenario) {
                case "INSUFFICIENT_FUNDS" -> Outcome.INSUFFICIENT_FUNDS;
                case "TEMPORARY_FAILURE" -> Outcome.TEMPORARY_FAILURE;
                case "TIMEOUT", "INIT_TIMEOUT_THEN_SUCCESS" -> Outcome.TIMEOUT;
                default -> Outcome.SUCCESS;
            };
            return record(key, reference, scenario, outcome, scenario.equals("INIT_TIMEOUT_THEN_SUCCESS") ? Outcome.SUCCESS : outcome);
        });
    }
    public GatewayResult confirm(String key, String reference) {
        return responses.computeIfAbsent(key, ignored -> {
            String scenario = scenarios.getOrDefault(root(key), "SUCCESS");
            Outcome outcome = switch (scenario) {
                case "CONFIRMATION_FAILED", "REFUND_FAILURE", "REFUND_TIMEOUT_THEN_SUCCESS" -> Outcome.CONFIRMATION_FAILED;
                case "CONFIRM_TIMEOUT_THEN_SUCCESS", "CONFIRM_TIMEOUT_THEN_FAILURE" -> Outcome.TIMEOUT;
                default -> Outcome.SUCCESS;
            };
            Outcome resolved = switch (scenario) {
                case "CONFIRM_TIMEOUT_THEN_SUCCESS" -> Outcome.SUCCESS;
                case "CONFIRM_TIMEOUT_THEN_FAILURE" -> Outcome.CONFIRMATION_FAILED;
                default -> outcome;
            };
            return record(key, reference, scenario, outcome, resolved);
        });
    }
    public GatewayResult refund(String key, String reference, BigDecimal amount) {
        return responses.computeIfAbsent(key, ignored -> {
            String scenario = scenarios.getOrDefault(root(key), "SUCCESS");
            Outcome outcome = switch (scenario) {
                case "REFUND_FAILURE" -> Outcome.REFUND_FAILURE;
                case "REFUND_TIMEOUT_THEN_SUCCESS" -> Outcome.TIMEOUT;
                default -> Outcome.REFUND_SUCCESS;
            };
            return record(key, reference, scenario, outcome, scenario.equals("REFUND_TIMEOUT_THEN_SUCCESS") ? Outcome.REFUND_SUCCESS : outcome);
        });
    }
    public GatewayResult inquiry(String key, String reference) {
        return providerOutcomes.getOrDefault(key, new GatewayResult(Outcome.UNKNOWN, reference, "UNKNOWN"));
    }
    private GatewayResult record(String key, String reference, String scenario, Outcome response, Outcome resolved) {
        providerOutcomes.put(key, new GatewayResult(resolved, reference, scenario));
        return new GatewayResult(response, reference, scenario);
    }
    private String root(String key) { return key.substring(0, key.lastIndexOf('-')); }
}
