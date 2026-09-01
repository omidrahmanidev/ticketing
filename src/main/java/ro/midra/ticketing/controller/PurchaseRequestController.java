package ro.midra.ticketing.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.midra.ticketing.dto.PurchaseRequestDto;
import ro.midra.ticketing.kafka.KafkaProducer;
import ro.midra.ticketing.kafka.PurchaseRequestMessage;

import java.util.Map;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final KafkaProducer kafkaProducer;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody PurchaseRequestDto request
    ) {

        PurchaseRequestMessage message =
                new PurchaseRequestMessage(
                        request.requestId(),
                        request.userId(),
                        request.eventId(),
                        request.seatIds()
                );

        /*
         * HTTP request does not touch MySQL for reservation.
         *
         * Request -> Kafka -> Worker -> MySQL
         */
        kafkaProducer.publish(message);

        return ResponseEntity.accepted().body(
                Map.of(
                        "requestId", request.requestId(),
                        "status", "PROCESSING"
                )
        );
    }
}