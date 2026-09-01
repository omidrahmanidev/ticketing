package ro.midra.ticketing.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReservationSeatId implements Serializable {

    private Long reservationId;

    private Long seatId;
}
