package ro.midra.ticketing.application.lock;

import java.time.Duration;
import java.util.List;

public interface SeatLockPort {

    record SeatLockResult(boolean redisAvailable, boolean acquired, List<Long> conflictingSeatIds) {
    }

    SeatLockResult tryLock(List<Long> seatIds, String owner, Duration ttl);

    void unlock(List<Long> seatIds);
}
