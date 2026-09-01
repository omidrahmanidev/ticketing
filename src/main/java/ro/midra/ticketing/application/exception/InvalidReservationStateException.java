package ro.midra.ticketing.application.exception;

public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(String message) {
        super(message);
    }
}
