package io.github.chikachi.ticketforge.payment.domain;

import io.github.chikachi.ticketforge.order.domain.TicketOrder;
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
@Table(name = "payment_records")
public class PaymentRecord {

    public static final String SIMULATOR_PROVIDER = "TICKETFORGE_SIMULATOR";
    public static final String DEFAULT_CURRENCY = "CNY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private TicketOrder order;

    @Column(nullable = false, unique = true, length = 120)
    private String paymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(columnDefinition = "text")
    private String callbackPayload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(length = 120)
    private String providerEventId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private Instant processedAt;

    @Column(length = 255)
    private String failureReason;

    protected PaymentRecord() {
    }

    public PaymentRecord(TicketOrder order, String paymentTransactionId, BigDecimal amount, Instant createdAt) {
        this.order = order;
        this.paymentTransactionId = paymentTransactionId;
        this.status = PaymentStatus.PENDING;
        this.provider = SIMULATOR_PROVIDER;
        this.amount = amount;
        this.currency = DEFAULT_CURRENCY;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isSuccessful() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public void markSuccess(String providerEventId, String callbackPayload, Instant processedAt) {
        this.status = PaymentStatus.SUCCESS;
        this.providerEventId = providerEventId;
        this.callbackPayload = callbackPayload;
        this.processedAt = processedAt;
        this.updatedAt = processedAt;
        this.failureReason = null;
    }

    public void markFailed(String providerEventId, String callbackPayload, String failureReason, Instant processedAt) {
        this.status = PaymentStatus.FAILED;
        this.providerEventId = providerEventId;
        this.callbackPayload = callbackPayload;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
        this.updatedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public TicketOrder getOrder() {
        return order;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
