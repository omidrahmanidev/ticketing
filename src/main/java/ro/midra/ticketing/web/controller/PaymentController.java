package ro.midra.ticketing.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.midra.ticketing.application.dto.PaymentDto.ConfirmPaymentRequest;
import ro.midra.ticketing.application.dto.PaymentDto.PaymentResponse;
import ro.midra.ticketing.application.service.PaymentService;
import ro.midra.ticketing.application.service.PaymentEventStore;
import ro.midra.ticketing.application.exception.NotFoundException;
import ro.midra.ticketing.application.projection.PaymentProjectionState;
import ro.midra.ticketing.domain.Payment;
import ro.midra.ticketing.domain.repository.PaymentRepository;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentEventStore paymentEventStore;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<PaymentResponse> status(@PathVariable Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        return ResponseEntity.ok(response(payment));
    }

    @GetMapping("/{paymentId}/replay")
    public ResponseEntity<PaymentResponse> replay(@PathVariable Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        PaymentProjectionState state = paymentEventStore.replay(paymentId);
        return ResponseEntity.ok(new PaymentResponse(paymentId, state.getStatus(), state.getAmount(),
                state.getProviderReference(), state.getRetryCount(), state.getLastError()));
    }

    private PaymentResponse response(Payment payment) {
        return new PaymentResponse(payment.getPaymentId(), payment.getStatus(), payment.getAmount(),
                payment.getProviderReference(), payment.getRetryCount(), payment.getLastError());
    }
}
