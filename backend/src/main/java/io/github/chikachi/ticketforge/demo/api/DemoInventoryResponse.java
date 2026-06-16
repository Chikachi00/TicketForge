package io.github.chikachi.ticketforge.demo.api;

public record DemoInventoryResponse(
        int totalStock,
        int availableStock,
        int reservedStock,
        int soldStock,
        int calculatedTotal,
        boolean inventoryConsistent
) {
}
