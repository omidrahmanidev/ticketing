package ro.midra.ticketing.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ro.midra.ticketing.application.lock.SeatLockPort;
import ro.midra.ticketing.application.lock.SeatLockPort.SeatLockOutcome.Acquired;
import ro.midra.ticketing.application.lock.SeatLockPort.SeatLockOutcome.Rejected;
import ro.midra.ticketing.application.lock.SeatLockPort.SeatLockOutcome.Unavailable;

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
    public SeatLockOutcome tryLock(List<Long> seatIds, String owner, Duration ttl) {
        List<Long> acquired = new ArrayList<>();
        try {
            for (Long seatId : seatIds) {
                String key = KEY_PREFIX + seatId;

                // SETNX seat-lock:{seatId} owner EX ttl -> atomic "set if not exists" with expiry
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);

                if (Boolean.TRUE.equals(ok)) {
                    acquired.add(seatId);
                    continue;
                }

                // Not a fresh acquire -- but if the key is already held by THIS SAME requestId,
                // treat it as acquired too. This makes retries of an in-flight or already-held
                // request cheap: they resolve entirely in Redis, without a database round trip
                // to check idempotency for the whole 500k-request storm.
                String currentOwner = redisTemplate.opsForValue().get(key);
                if (owner.equals(currentOwner)) {
                    acquired.add(seatId);
                    continue;
                }

                // Genuinely someone else's lock: bail out without touching the database at all.
                releaseKeys(acquired);
                return new Rejected(List.of(seatId));
            }
            return new Acquired();

        } catch (Exception ex) {
            log.warn("Redis unavailable, falling back to the queue-based path: {}", ex.getMessage());
            releaseKeys(acquired);
            return new Unavailable();
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

    @Override
    public void unlockOwned(List<Long> seatIds, String owner) {
        var script = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
        for (Long seatId : seatIds) redisTemplate.execute(script, List.of(KEY_PREFIX + seatId), owner);
    }

    private void releaseKeys(List<Long> seatIds) {
        for (Long seatId : seatIds) {
            redisTemplate.delete(KEY_PREFIX + seatId);
        }
    }
}
