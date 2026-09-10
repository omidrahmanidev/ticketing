package ro.midra.ticketing.payment.infrastructure.eventstore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_streams", indexes = @Index(
        name = "idx_payment_stream_reservation", columnList = "reservation_id"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentIdentityEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    public PaymentIdentityEntity(Long reservationId) { this.reservationId = reservationId; }
}
