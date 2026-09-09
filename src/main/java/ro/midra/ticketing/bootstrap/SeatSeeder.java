package ro.midra.ticketing.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.domain.Event;
import ro.midra.ticketing.domain.EventStatus;
import ro.midra.ticketing.domain.SeatStatus;
import ro.midra.ticketing.application.readmodel.SeatSnapshot;
import ro.midra.ticketing.application.service.SeatReadModelPort;
import ro.midra.ticketing.domain.repository.EventRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds 10,000 AVAILABLE seats for a sample concert on startup.
 * Idempotent: if the event already has seats, it does nothing, so restarts don't duplicate data.
 * Uses raw JDBC batch inserts instead of JPA saveAll() -- 10,000 individually tracked entities
 * would be slow; a handful of batched INSERT statements is fast and simple for a one-time seed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class SeatSeeder implements ApplicationRunner {

    private static final int TOTAL_SEATS = 10_000;
    private static final int BATCH_SIZE = 1_000;
    private static final int SEATS_PER_ROW = 50;

    private final JdbcTemplate jdbcTemplate;
    private final EventRepository eventRepository;
    private final SeatReadModelPort seatReadModelPort;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Event event = eventRepository.findByStatus(EventStatus.ON_SALE).stream()
                .findFirst()
                .orElseGet(this::createSampleEvent);

        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seats WHERE event_id = ?", Integer.class, event.getEventId());

        if (existingCount != null && existingCount > 0) {
            warmReadModel(event);
            log.info("Event {} already has {} seats, skipping seed", event.getEventId(), existingCount);
            return;
        }

        log.info("Seeding {} seats for event {}", TOTAL_SEATS, event.getEventId());

        String sql = "INSERT INTO seats (event_id, seat_number, status, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batchArgs = new ArrayList<>(BATCH_SIZE);

        for (int i = 1; i <= TOTAL_SEATS; i++) {
            batchArgs.add(new Object[]{
                    event.getEventId(),
                    seatNumber(i),
                    SeatStatus.AVAILABLE.name(),
                    0L,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            });
            if (batchArgs.size() == BATCH_SIZE || i == TOTAL_SEATS) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        warmReadModel(event);

        log.info("Seeded {} seats for event {}", TOTAL_SEATS, event.getEventId());
    }

    private Event createSampleEvent() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder()
                .name("Sample Concert")
                .startsAt(now.plusDays(7))
                .status(EventStatus.ON_SALE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Event saved = eventRepository.save(event);
        log.info("Created sample event {} for seeding", saved.getEventId());
        return saved;
    }

    private String seatNumber(int index) {
        int row = (index - 1) / SEATS_PER_ROW + 1;
        int seatInRow = (index - 1) % SEATS_PER_ROW + 1;
        return "R" + row + "-" + seatInRow;
    }

    private void warmReadModel(Event event) {
        List<SeatSnapshot> snapshots = jdbcTemplate.query(
                "SELECT seat_id, seat_number, status FROM seats WHERE event_id = ? ORDER BY seat_id",
                (resultSet, rowNum) -> new SeatSnapshot(
                        resultSet.getLong("seat_id"),
                        resultSet.getString("seat_number"),
                        SeatStatus.valueOf(resultSet.getString("status"))
                ),
                event.getEventId()
        );
        seatReadModelPort.upsertSeats(event.getEventId(), snapshots);
    }
}
