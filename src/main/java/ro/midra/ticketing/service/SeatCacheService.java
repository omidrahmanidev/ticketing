package ro.midra.ticketing.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ro.midra.ticketing.domain.Seat;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SeatCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(15);

    public void cacheSeat(Seat seat) {

        String key = key(
                seat.getEvent().getEventId(),
                seat.getSeatId()
        );

        /*
         * Redis is NOT the source of truth.
         * If this operation fails, MySQL remains authoritative.
         */
        try {

            redisTemplate.opsForValue().set(
                    key,
                    seat.getStatus().name(),
                    CACHE_TTL
            );

        } catch (Exception ignored) {

            /*
             * Redis failure must not break ticket correctness.
             */
        }
    }

    public String getSeatStatus(
            Long eventId,
            Long seatId
    ) {

        try {
            return redisTemplate.opsForValue()
                    .get(key(eventId, seatId));

        } catch (Exception ignored) {

            /*
             * Cache miss/failure -> caller should fallback to MySQL.
             */
            return null;
        }
    }

    public void evictSeat(
            Long eventId,
            Long seatId
    ) {

        try {

            redisTemplate.delete(
                    key(eventId, seatId)
            );

        } catch (Exception ignored) {
        }
    }

    private String key(
            Long eventId,
            Long seatId
    ) {

        return "seat:"
                + eventId
                + ":"
                + seatId;
    }
}