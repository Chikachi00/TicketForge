package io.github.chikachi.ticketforge.payment.api;

import io.github.chikachi.ticketforge.payment.application.PaymentSimulatorService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@RestController
@RequestMapping("/api/payment-simulator")
public class PaymentSimulatorController {

    private final PaymentSimulatorService paymentSimulatorService;

    public PaymentSimulatorController(PaymentSimulatorService paymentSimulatorService) {
        this.paymentSimulatorService = paymentSimulatorService;
    }

    @PostMapping("/{paymentTransactionId}/success")
    public PaymentCallbackResponse simulateSuccess(@PathVariable String paymentTransactionId) {
        return paymentSimulatorService.simulateSuccess(paymentTransactionId);
    }

    @PostMapping("/{paymentTransactionId}/failure")
    public PaymentCallbackResponse simulateFailure(
            @PathVariable String paymentTransactionId,
            @RequestBody(required = false) PaymentFailureSimulationRequest request
    ) {
        return paymentSimulatorService.simulateFailure(
                paymentTransactionId,
                request == null ? null : request.reason()
        );
    }
}
