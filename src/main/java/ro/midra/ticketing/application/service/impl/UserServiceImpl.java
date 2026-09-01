package ro.midra.ticketing.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.midra.ticketing.application.dto.AuthDto.LoginRequest;
import ro.midra.ticketing.application.dto.AuthDto.LoginResponse;
import ro.midra.ticketing.application.service.UserService;
import ro.midra.ticketing.domain.User;
import ro.midra.ticketing.domain.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public LoginResponse loginOrRegister(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    User newUser = User.builder()
                            .phoneNumber(request.phoneNumber())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return userRepository.save(newUser);
                });

        return new LoginResponse(user.getUserId(), user.getPhoneNumber());
    }
}
