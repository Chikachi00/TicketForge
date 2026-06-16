package io.github.chikachi.ticketforge.demo.api;

public record DemoTicketTierInventoryResponse(
        Long id,
        String code,
        String name,
        int totalStock,
        int availableStock,
        int reservedStock,
        int soldStock,
        int calculatedTotal,
        boolean inventoryConsistent
) {
}
