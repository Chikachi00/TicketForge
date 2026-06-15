package io.github.chikachi.ticketforge.inventory.domain;

import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ticket_inventory")
public class TicketInventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_tier_id", nullable = false, unique = true)
    private TicketTierEntity ticketTier;

    @Column(nullable = false)
    private int availableStock;

    @Column(nullable = false)
    private int reservedStock;

    @Column(nullable = false)
    private int soldStock;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TicketInventoryEntity() {
    }

    public TicketInventoryEntity(Long id, int availableStock, int reservedStock, int soldStock, long version) {
        this.id = id;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.soldStock = soldStock;
        this.version = version;
        this.updatedAt = Instant.now();
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public int getSoldStock() {
        return soldStock;
    }

    public void setTicketTier(TicketTierEntity ticketTier) {
        this.ticketTier = ticketTier;
    }
}

