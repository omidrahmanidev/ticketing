package ro.midra.ticketing.domain.repository;

import ro.midra.ticketing.domain.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long userId);

    Optional<User> findByPhoneNumber(String phoneNumber);

    User save(User user);
}
