package io.github.chikachi.ticketforge.inventory.application;

public record StockSnapshot(
        int availableStock,
        int reservedStock,
        int soldStock
) {
}

