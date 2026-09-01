package ro.midra.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.midra.ticketing.domain.*;
import ro.midra.ticketing.kafka.KafkaProducer;
import ro.midra.ticketing.kafka.PurchaseRequestMessage;
import ro.midra.ticketing.repository.*;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final ReservationService reservationService;
    private final KafkaProducer kafkaProducer;

    public void submit(PurchaseRequestMessage message) {

        /*
         * Kafka is the async buffer.
         * We do not reserve the seat inside the HTTP request.
         */
        kafkaProducer.publish(message);
    }

    @Transactional
    public void process(PurchaseRequestMessage message) {

        /*
         * Idempotency:
         *
         * Kafka may deliver the same message more than once.
         * requestId is the idempotency key and is the PK.
         */
        if (purchaseRequestRepository.existsById(message.requestId())) {
            return;
        }


        PurchaseRequest request = PurchaseRequest.builder()
                .requestId(message.requestId())
                .status(PurchaseRequestStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        purchaseRequestRepository.save(request);

        try {

            Reservation reservation =
                    reservationService.createReservation(
                            message.requestId(),
                            message.userId(),
                            message.eventId(),
                            message.seatIds()
                    );

            request.setReservation(reservation);
            request.setStatus(PurchaseRequestStatus.SUCCEEDED);
            request.setUpdatedAt(LocalDateTime.now());

        } catch (RuntimeException ex) {

            request.setStatus(PurchaseRequestStatus.FAILED);
            request.setUpdatedAt(LocalDateTime.now());

            throw ex;
        }
    }
}