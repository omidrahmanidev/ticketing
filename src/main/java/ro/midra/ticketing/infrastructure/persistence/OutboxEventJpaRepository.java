package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.OutboxEvent;
import ro.midra.ticketing.domain.repository.OutboxEventRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long>, OutboxEventRepository {
    @org.springframework.data.jpa.repository.Query("select e from OutboxEvent e where e.published = false "
            + "and (e.availableAt is null or e.availableAt <= :now) order by e.id")
    java.util.List<OutboxEvent> findDue(java.time.LocalDateTime now, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from OutboxEvent e where e.id = :id")
    java.util.Optional<OutboxEvent> findAndLockById(Long id);
}
