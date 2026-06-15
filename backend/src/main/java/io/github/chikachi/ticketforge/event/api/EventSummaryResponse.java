package io.github.chikachi.ticketforge.event.api;

import java.time.Instant;
import java.util.List;

public record EventSummaryResponse(
        Long id,
        String slug,
        String name,
        String venue,
        String description,
        Instant performanceAt,
        Instant salesStartAt,
        String status,
        List<TicketTierSummaryResponse> ticketTiers
) {
}

