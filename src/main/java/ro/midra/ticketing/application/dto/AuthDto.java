package ro.midra.ticketing.application.dto;

public class AuthDto {

    public record LoginRequest(String phoneNumber) {
    }

    public record LoginResponse(Long userId, String phoneNumber) {
    }

    private AuthDto() {
    }
}
