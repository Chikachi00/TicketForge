package io.github.chikachi.ticketforge.demo.api;

public record DemoPaymentStatsResponse(
        long pending,
        long success,
        long failed
) {
}
