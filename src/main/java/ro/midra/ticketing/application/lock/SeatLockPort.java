package ro.midra.ticketing.application.lock;

import java.time.Duration;
import java.util.List;

public interface SeatLockPort {

    sealed interface SeatLockOutcome {
        record Acquired() implements SeatLockOutcome {
        }

        record Rejected(List<Long> conflictingSeatIds) implements SeatLockOutcome {
        }

        record Unavailable() implements SeatLockOutcome {
        }
    }

    SeatLockOutcome tryLock(List<Long> seatIds, String owner, Duration ttl);

    void unlock(List<Long> seatIds);

    /** Retryable release; must never delete a newer owner's lock. */
    void unlockOwned(List<Long> seatIds, String owner);
}
