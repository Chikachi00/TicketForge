package io.github.chikachi.ticketforge.loadtest.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LoadTestResetRequest(
        @NotBlank String eventSlug,
        @NotBlank String ticketCode,
        @Min(1) @Max(1_000_000) int totalStock,
        @Min(1) @Max(100_000) int userCount
) {
}
