package ro.midra.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "reservation_seats",
        indexes = {
                @Index(name = "idx_reservation_seats_seat", columnList = "seat_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
}
