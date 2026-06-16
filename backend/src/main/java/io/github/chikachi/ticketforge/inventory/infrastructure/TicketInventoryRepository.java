package io.github.chikachi.ticketforge.inventory.infrastructure;

import io.github.chikachi.ticketforge.inventory.domain.TicketInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketInventoryRepository extends JpaRepository<TicketInventoryEntity, Long> {

    @Modifying
    @Query(value = """
            UPDATE ticket_inventory
            SET available_stock = available_stock - :quantity,
                reserved_stock = reserved_stock + :quantity,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE ticket_tier_id = :ticketTierId
              AND available_stock >= :quantity
            """, nativeQuery = true)
    int reserve(Long ticketTierId, int quantity);

    @Modifying
    @Query(value = """
            UPDATE ticket_inventory
            SET available_stock = available_stock + :quantity,
                reserved_stock = reserved_stock - :quantity,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE ticket_tier_id = :ticketTierId
              AND reserved_stock >= :quantity
            """, nativeQuery = true)
    int release(Long ticketTierId, int quantity);
}
