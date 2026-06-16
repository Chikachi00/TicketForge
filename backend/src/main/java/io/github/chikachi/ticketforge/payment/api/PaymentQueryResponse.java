package io.github.chikachi.ticketforge.payment.api;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentQueryResponse(
        String paymentTransactionId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        String status,
        String provider,
        Instant createdAt,
        Instant processedAt
) {
}
