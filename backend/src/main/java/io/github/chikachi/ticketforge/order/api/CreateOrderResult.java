package io.github.chikachi.ticketforge.order.api;

public record CreateOrderResult(
        OrderResponse response,
        boolean created
) {
}
