package ro.midra.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "purchase_request_seats",
        indexes = {
                @Index(name = "idx_purchase_request_seats_seat", columnList = "seat_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestSeat {

    @EmbeddedId
    private PurchaseRequestSeatId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("requestId")
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
}
