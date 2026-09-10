package ro.midra.ticketing.payment.infrastructure.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.payment.domain.PaymentEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PaymentEventSerializer {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    // Explicit sealed event family: persisted type names cannot instantiate arbitrary classes.
    private final Map<String, Class<?>> types = Arrays.stream(PaymentEvent.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, type -> type));

    public String serialize(PaymentEvent event) {
        try { return mapper.writeValueAsString(event); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize payment event", e); }
    }
    public PaymentEvent deserialize(PaymentEventEntity entity) {
        Class<?> type = types.get(entity.getEventType());
        if (type == null) throw new IllegalStateException("Unsupported payment event " + entity.getEventType()
                + "; legacy databases must run the documented payment cutover migration");
        try { return (PaymentEvent) mapper.readValue(entity.getPayload(), type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read payment event " + entity.getId(), e); }
    }
}
