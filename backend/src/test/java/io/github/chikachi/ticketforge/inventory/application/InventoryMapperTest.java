package io.github.chikachi.ticketforge.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.inventory.domain.TicketInventoryEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryMapperTest {

    private final InventoryMapper inventoryMapper = new InventoryMapper();

    @Test
    void mapsInventoryEntityToSnapshot() {
        TicketTierEntity tier = new TicketTierEntity(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100);
        tier.attachInventory(new TicketInventoryEntity(1L, 91, 4, 5, 2));

        StockSnapshot snapshot = inventoryMapper.toStockSnapshot(tier);

        assertThat(snapshot.availableStock()).isEqualTo(91);
        assertThat(snapshot.reservedStock()).isEqualTo(4);
        assertThat(snapshot.soldStock()).isEqualTo(5);
    }

    @Test
    void mapsMissingInventoryToZeroSnapshot() {
        TicketTierEntity tier = new TicketTierEntity(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100);

        StockSnapshot snapshot = inventoryMapper.toStockSnapshot(tier);

        assertThat(snapshot.availableStock()).isZero();
        assertThat(snapshot.reservedStock()).isZero();
        assertThat(snapshot.soldStock()).isZero();
    }
}

