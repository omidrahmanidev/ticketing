package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.payment.application.PaymentQueryService;
import ro.midra.ticketing.payment.application.PaymentReplayService;
import ro.midra.ticketing.payment.application.StartPaymentHandler;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final StartPaymentHandler start;
    private final PaymentQueryService queries;
    private final PaymentReplayService replay;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> startPayment(@RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.accepted().body(start.startPayment(request));
    }

    @GetMapping("/{paymentId}/status")
    public PaymentResponse status(@PathVariable Long paymentId) {
        return queries.status(paymentId);
    }

    @GetMapping("/{paymentId}/replay")
    public PaymentResponse replay(@PathVariable Long paymentId) {
        return replay.replay(paymentId);
    }

    @PostMapping("/{paymentId}/rebuild")
    public PaymentResponse rebuild(@PathVariable Long paymentId) {
        return replay.rebuild(paymentId);
    }
}
