package ro.midra.ticketing.application.exception;

public class SeatNotAvailableException extends RuntimeException {

    public SeatNotAvailableException(String message) {
        super(message);
    }
}
