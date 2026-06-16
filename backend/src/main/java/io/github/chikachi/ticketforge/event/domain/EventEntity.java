package io.github.chikachi.ticketforge.event.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String venue;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Instant performanceAt;

    @Column(nullable = false)
    private Instant salesStartAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketTierEntity> ticketTiers = new ArrayList<>();

    protected EventEntity() {
    }

    public EventEntity(Long id, String slug, String name, String venue, String description,
                       Instant performanceAt, Instant salesStartAt, EventStatus status) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.venue = venue;
        this.description = description;
        this.performanceAt = performanceAt;
        this.salesStartAt = salesStartAt;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addTicketTier(TicketTierEntity ticketTier) {
        ticketTier.setEvent(this);
        ticketTiers.add(ticketTier);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getVenue() {
        return venue;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPerformanceAt() {
        return performanceAt;
    }

    public Instant getSalesStartAt() {
        return salesStartAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public List<TicketTierEntity> getTicketTiers() {
        return ticketTiers;
    }
}
