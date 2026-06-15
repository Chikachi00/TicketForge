package io.github.chikachi.ticketforge.common.api;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}

