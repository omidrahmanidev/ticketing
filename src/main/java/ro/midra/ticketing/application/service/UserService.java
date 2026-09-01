package ro.midra.ticketing.application.service;

import ro.midra.ticketing.application.dto.AuthDto.LoginRequest;
import ro.midra.ticketing.application.dto.AuthDto.LoginResponse;

public interface UserService {

    LoginResponse loginOrRegister(LoginRequest request);
}
