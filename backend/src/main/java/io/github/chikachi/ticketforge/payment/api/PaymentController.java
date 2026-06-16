package io.github.chikachi.ticketforge.payment.api;

import io.github.chikachi.ticketforge.payment.application.PaymentApplicationService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    public PaymentController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @PostMapping("/orders/{orderNumber}")
    public ResponseEntity<PaymentSessionResponse> createPaymentSession(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String orderNumber
    ) {
        PaymentSessionResult result = paymentApplicationService.createPaymentSession(userEmail, idempotencyKey, orderNumber);
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/payments/" + result.response().paymentTransactionId()))
                    .body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/{paymentTransactionId}")
    public PaymentQueryResponse getPayment(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable String paymentTransactionId
    ) {
        return paymentApplicationService.getPayment(userEmail, paymentTransactionId);
    }
}
