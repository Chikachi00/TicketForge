package io.github.chikachi.ticketforge.payment.api;

import io.github.chikachi.ticketforge.payment.application.PaymentCallbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentCallbackController {

    private final PaymentCallbackService paymentCallbackService;

    public PaymentCallbackController(PaymentCallbackService paymentCallbackService) {
        this.paymentCallbackService = paymentCallbackService;
    }

    @PostMapping("/callback")
    public PaymentCallbackResponse handleCallback(
            @RequestHeader("X-Payment-Signature") String signature,
            @Valid @RequestBody PaymentCallbackRequest request
    ) {
        return paymentCallbackService.handleCallback(request, signature);
    }
}
