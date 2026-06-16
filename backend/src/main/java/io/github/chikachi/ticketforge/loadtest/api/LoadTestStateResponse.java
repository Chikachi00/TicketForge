package io.github.chikachi.ticketforge.loadtest.api;

public record LoadTestStateResponse(
        String eventSlug,
        Long ticketTierId,
        int totalStock,
        int availableStock,
        int reservedStock,
        int soldStock,
        long pendingOrders,
        long paidOrders,
        long cancelledOrders,
        long paymentSuccessCount,
        long paymentFailedCount,
        int calculatedTotal,
        boolean inventoryConsistent,
        boolean negativeInventoryDetected,
        boolean oversellDetected
) {
}
