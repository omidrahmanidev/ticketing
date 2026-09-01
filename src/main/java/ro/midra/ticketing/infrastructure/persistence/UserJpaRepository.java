package ro.midra.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.midra.ticketing.domain.User;
import ro.midra.ticketing.domain.repository.UserRepository;

public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {
}
