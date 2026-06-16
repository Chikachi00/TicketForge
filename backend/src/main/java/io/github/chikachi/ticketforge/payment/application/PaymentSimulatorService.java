package io.github.chikachi.ticketforge.payment.application;

import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.payment.api.PaymentCallbackRequest;
import io.github.chikachi.ticketforge.payment.api.PaymentCallbackResponse;
import io.github.chikachi.ticketforge.payment.domain.PaymentCallbackStatus;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.infrastructure.PaymentRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentSimulatorService {

    private static final DateTimeFormatter EVENT_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentSignatureService paymentSignatureService;
    private final PaymentCallbackService paymentCallbackService;
    private final Clock clock;

    public PaymentSimulatorService(PaymentRecordRepository paymentRecordRepository,
                                   PaymentSignatureService paymentSignatureService,
                                   PaymentCallbackService paymentCallbackService,
                                   Clock clock) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentSignatureService = paymentSignatureService;
        this.paymentCallbackService = paymentCallbackService;
        this.clock = clock;
    }

    public PaymentCallbackResponse simulateSuccess(String paymentTransactionId) {
        return simulate(paymentTransactionId, PaymentCallbackStatus.SUCCESS, null);
    }

    public PaymentCallbackResponse simulateFailure(String paymentTransactionId, String reason) {
        return simulate(paymentTransactionId, PaymentCallbackStatus.FAILED,
                reason == null || reason.isBlank() ? "SIMULATED_PAYMENT_DECLINED" : reason);
    }

    private PaymentCallbackResponse simulate(String paymentTransactionId, PaymentCallbackStatus status, String failureReason) {
        PaymentRecord payment = paymentRecordRepository.findDetailedByPaymentTransactionId(paymentTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment not found: " + paymentTransactionId));
        Instant occurredAt = Instant.now(clock);
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                generateProviderEventId(occurredAt),
                payment.getPaymentTransactionId(),
                payment.getOrder().getOrderNumber(),
                status.name(),
                payment.getAmount(),
                payment.getCurrency(),
                occurredAt,
                failureReason
        );
        return paymentCallbackService.handleCallback(request, paymentSignatureService.sign(request));
    }

    private String generateProviderEventId(Instant occurredAt) {
        return "EVT-" + EVENT_DATE.format(occurredAt) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
