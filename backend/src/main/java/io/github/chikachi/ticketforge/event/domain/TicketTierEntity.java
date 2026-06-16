package io.github.chikachi.ticketforge.event.domain;

import io.github.chikachi.ticketforge.inventory.domain.TicketInventoryEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ticket_tiers")
public class TicketTierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private EventEntity event;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int totalStock;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "ticketTier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TicketInventoryEntity inventory;

    protected TicketTierEntity() {
    }

    public TicketTierEntity(Long id, String code, String name, BigDecimal price, int totalStock) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.price = price;
        this.totalStock = totalStock;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void attachInventory(TicketInventoryEntity inventory) {
        inventory.setTicketTier(this);
        this.inventory = inventory;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public EventEntity getEvent() {
        return event;
    }

    public TicketInventoryEntity getInventory() {
        return inventory;
    }

    public void setEvent(EventEntity event) {
        this.event = event;
    }
}
