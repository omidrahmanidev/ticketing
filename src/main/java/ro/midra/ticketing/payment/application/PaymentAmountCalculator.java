package ro.midra.ticketing.payment.application;

import org.springframework.stereotype.Component;
import ro.midra.ticketing.domain.Reservation;
import java.math.BigDecimal;

@Component
public class PaymentAmountCalculator {
    /** Demo pricing: this project has no monetary seat/event price, so one seat costs one unit. */
    public BigDecimal total(Reservation reservation) { return BigDecimal.valueOf(reservation.getSeats().size()).setScale(2); }
}
