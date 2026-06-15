package io.github.chikachi.ticketforge.event.api;

import java.math.BigDecimal;

public record TicketTierSummaryResponse(
        Long id,
        String code,
        String name,
        BigDecimal price,
        int totalStock,
        int availableStock
) {
}

