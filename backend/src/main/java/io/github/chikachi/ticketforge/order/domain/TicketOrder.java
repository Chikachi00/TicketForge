package io.github.chikachi.ticketforge.order.domain;

import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ticket_orders")
public class TicketOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private EventEntity event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_tier_id", nullable = false)
    private TicketTierEntity ticketTier;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant paidAt;

    private Instant cancelledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TicketOrder() {
    }

    public TicketOrder(String orderNumber, AppUser user, EventEntity event, TicketTierEntity ticketTier,
                       int quantity, BigDecimal unitPrice, BigDecimal totalAmount, String idempotencyKey,
                       Instant expiresAt, Instant createdAt) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.event = event;
        this.ticketTier = ticketTier;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isPendingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    public boolean isCancellable() {
        return status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.CANCELLED;
    }

    public void cancel(Instant cancelledAt) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.updatedAt = cancelledAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public AppUser getUser() {
        return user;
    }

    public EventEntity getEvent() {
        return event;
    }

    public TicketTierEntity getTicketTier() {
        return ticketTier;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
