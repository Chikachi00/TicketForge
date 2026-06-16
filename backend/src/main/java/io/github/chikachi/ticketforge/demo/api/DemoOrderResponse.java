package io.github.chikachi.ticketforge.demo.api;

import java.math.BigDecimal;
import java.time.Instant;

public record DemoOrderResponse(
        String orderNumber,
        String ticketTierCode,
        String ticketTierName,
        int quantity,
        String status,
        BigDecimal totalAmount,
        String latestPaymentTransactionId,
        String latestPaymentStatus,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt
) {
}
