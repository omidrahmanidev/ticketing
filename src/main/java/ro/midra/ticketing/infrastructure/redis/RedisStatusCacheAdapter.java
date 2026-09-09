package ro.midra.ticketing.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.dto.ReservationDto.HoldSeatsResponse;
import ro.midra.ticketing.application.service.StatusCachePort;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStatusCacheAdapter implements StatusCachePort {

    private static final String KEY_PREFIX = "hold-status:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<HoldSeatsResponse> get(String requestId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + requestId);
            return value == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(value, HoldSeatsResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to read hold status from Redis for requestId={}: {}", requestId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String requestId, HoldSeatsResponse response, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + requestId, objectMapper.writeValueAsString(response), ttl);
        } catch (Exception ex) {
            log.warn("Failed to cache hold status in Redis for requestId={}: {}", requestId, ex.getMessage());
        }
    }
}
