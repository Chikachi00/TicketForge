package io.github.chikachi.ticketforge.demo.api;

public record DemoOrderStatsResponse(
        long total,
        long pendingPayment,
        long paid,
        long cancelled,
        long refunded
) {
}
