package io.github.chikachi.ticketforge.loadtest.api;

public record LoadTestResetResponse(
        String eventSlug,
        Long eventId,
        Long ticketTierId,
        int totalStock,
        int userCount
) {
}
