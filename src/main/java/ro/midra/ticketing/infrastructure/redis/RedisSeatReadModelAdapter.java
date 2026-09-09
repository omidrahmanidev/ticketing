package ro.midra.ticketing.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.readmodel.SeatSnapshot;
import ro.midra.ticketing.application.service.SeatReadModelPort;
import ro.midra.ticketing.domain.SeatStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatReadModelAdapter implements SeatReadModelPort {

    private static final String KEY_PREFIX = "seat-read-model:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void upsertSeats(Long eventId, List<SeatSnapshot> seats) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (SeatSnapshot seat : seats) {
                values.put(seat.seatId().toString(), objectMapper.writeValueAsString(seat));
            }
            if (!values.isEmpty()) {
                hashOperations().putAll(key(eventId), values);
            }
        } catch (Exception ex) {
            log.warn("Failed to warm seat read model for eventId={}: {}", eventId, ex.getMessage());
        }
    }

    @Override
    public void updateStatus(Long eventId, Long seatId, SeatStatus status) {
        try {
            String current = hashOperations().get(key(eventId), seatId.toString());
            if (current == null) {
                log.warn("Seat read model is not warmed for eventId={}, seatId={}", eventId, seatId);
                return;
            }
            SeatSnapshot snapshot = objectMapper.readValue(current, SeatSnapshot.class);
            hashOperations().put(key(eventId), seatId.toString(),
                    objectMapper.writeValueAsString(new SeatSnapshot(seatId, snapshot.seatNumber(), status)));
        } catch (Exception ex) {
            log.warn("Failed to update seat read model for eventId={}, seatId={}: {}",
                    eventId, seatId, ex.getMessage());
        }
    }

    @Override
    public Optional<List<SeatSnapshot>> listSeats(Long eventId) {
        try {
            List<String> values = hashOperations().values(key(eventId));
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(values.stream()
                    .map(this::readSnapshot)
                    .toList());
        } catch (Exception ex) {
            log.warn("Failed to read seat read model for eventId={}: {}", eventId, ex.getMessage());
            return Optional.empty();
        }
    }

    private SeatSnapshot readSnapshot(String value) {
        try {
            return objectMapper.readValue(value, SeatSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize seat read model entry", ex);
        }
    }

    private HashOperations<String, String, String> hashOperations() {
        return redisTemplate.opsForHash();
    }

    private String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
