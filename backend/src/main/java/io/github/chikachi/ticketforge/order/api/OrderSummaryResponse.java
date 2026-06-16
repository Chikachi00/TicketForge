package io.github.chikachi.ticketforge.order.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        String orderNumber,
        String eventName,
        String ticketTierCode,
        String ticketTierName,
        int quantity,
        BigDecimal totalAmount,
        String status,
        Instant expiresAt,
        Instant cancelledAt,
        Instant createdAt
) {
}
