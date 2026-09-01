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
public class PurchaseRequestSeatId implements Serializable {

    private String requestId;

    private Long seatId;
}
