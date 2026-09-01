package ro.midra.ticketing.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.lock.SeatLockPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatLockAdapter implements SeatLockPort {

    private static final String KEY_PREFIX = "seat-lock:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public SeatLockResult tryLock(List<Long> seatIds, String owner, Duration ttl) {
        List<Long> acquired = new ArrayList<>();
        try {
            for (Long seatId : seatIds) {
                // SETNX seat-lock:{seatId} owner EX ttl -> atomic "set if not exists" with expiry
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(KEY_PREFIX + seatId, owner, ttl);

                if (Boolean.TRUE.equals(ok)) {
                    acquired.add(seatId);
                } else {
                    releaseKeys(acquired);
                    return new SeatLockResult(true, false, List.of(seatId));
                }
            }
            return new SeatLockResult(true, true, List.of());

        } catch (Exception ex) {
            log.warn("Redis unavailable, falling back to database-only locking: {}", ex.getMessage());
            releaseKeys(acquired);
            return new SeatLockResult(false, false, List.of());
        }
    }

    @Override
    public void unlock(List<Long> seatIds) {
        try {
            releaseKeys(seatIds);
        } catch (Exception ex) {
            log.warn("Failed to release redis seat locks, they will expire via TTL: {}", ex.getMessage());
        }
    }

    private void releaseKeys(List<Long> seatIds) {
        for (Long seatId : seatIds) {
            redisTemplate.delete(KEY_PREFIX + seatId);
        }
    }
}
