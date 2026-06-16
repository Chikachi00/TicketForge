package io.github.chikachi.ticketforge.order.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long ticketTierId,
        @Min(1) @Max(6) int quantity
) {
}
