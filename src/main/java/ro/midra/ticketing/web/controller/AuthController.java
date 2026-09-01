package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.AuthDto.LoginRequest;
import ro.midra.ticketing.application.dto.AuthDto.LoginResponse;
import ro.midra.ticketing.application.service.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.loginOrRegister(request));
    }
}
