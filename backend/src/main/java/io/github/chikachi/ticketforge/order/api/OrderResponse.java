package io.github.chikachi.ticketforge.order.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderNumber,
        Long eventId,
        String eventName,
        Long ticketTierId,
        String ticketTierCode,
        String ticketTierName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        Instant expiresAt,
        Instant cancelledAt,
        Instant paidAt,
        Instant createdAt,
        boolean idempotentReplay
) {
}
