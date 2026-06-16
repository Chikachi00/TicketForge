package io.github.chikachi.ticketforge.demo.api;

import java.time.Instant;
import java.util.List;

public record DemoDashboardResponse(
        DemoEventResponse event,
        DemoInventoryResponse inventory,
        List<DemoTicketTierInventoryResponse> ticketTiers,
        DemoOrderStatsResponse orders,
        DemoPaymentStatsResponse payments,
        List<DemoOrderResponse> recentOrders,
        Instant generatedAt
) {
}
