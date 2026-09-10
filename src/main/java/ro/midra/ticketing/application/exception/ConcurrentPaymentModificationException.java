package ro.midra.ticketing.application.exception;

public class ConcurrentPaymentModificationException extends RuntimeException {

    public ConcurrentPaymentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
