package io.github.chikachi.ticketforge.inventory.application;

import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public StockSnapshot toStockSnapshot(TicketTierEntity ticketTier) {
        if (ticketTier.getInventory() == null) {
            return new StockSnapshot(0, 0, 0);
        }
        return new StockSnapshot(
                ticketTier.getInventory().getAvailableStock(),
                ticketTier.getInventory().getReservedStock(),
                ticketTier.getInventory().getSoldStock()
        );
    }
}

