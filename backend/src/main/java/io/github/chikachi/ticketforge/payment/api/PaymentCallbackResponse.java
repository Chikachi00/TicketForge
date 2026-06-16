package io.github.chikachi.ticketforge.payment.api;

import java.time.Instant;

public record PaymentCallbackResponse(
        String providerEventId,
        String paymentTransactionId,
        String orderNumber,
        String status,
        boolean idempotentReplay,
        Instant processedAt
) {
}
