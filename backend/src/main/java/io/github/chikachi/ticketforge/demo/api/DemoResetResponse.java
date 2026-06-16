package io.github.chikachi.ticketforge.demo.api;

import java.time.Instant;

public record DemoResetResponse(
        String eventSlug,
        int deletedOrders,
        int deletedPayments,
        int availableStock,
        int reservedStock,
        int soldStock,
        boolean inventoryConsistent,
        Instant resetAt
) {
}
