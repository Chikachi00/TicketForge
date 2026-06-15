package io.github.chikachi.ticketforge.event.application;

import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.api.EventDetailResponse;
import io.github.chikachi.ticketforge.event.api.EventSummaryResponse;
import io.github.chikachi.ticketforge.event.api.TicketTierDetailResponse;
import io.github.chikachi.ticketforge.event.api.TicketTierSummaryResponse;
import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.event.infrastructure.EventRepository;
import io.github.chikachi.ticketforge.inventory.application.InventoryMapper;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final InventoryMapper inventoryMapper;

    public EventService(EventRepository eventRepository, InventoryMapper inventoryMapper) {
        this.eventRepository = eventRepository;
        this.inventoryMapper = inventoryMapper;
    }

    public List<EventSummaryResponse> listEvents() {
        return eventRepository.findAllWithTicketTiers().stream()
                .map(this::toSummary)
                .toList();
    }

    public EventDetailResponse getEvent(Long eventId) {
        return eventRepository.findDetailedById(eventId)
                .map(this::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found: " + eventId));
    }

    public EventDetailResponse getEventBySlug(String slug) {
        return eventRepository.findDetailedBySlug(slug)
                .map(this::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found: " + slug));
    }

    private EventSummaryResponse toSummary(EventEntity event) {
        return new EventSummaryResponse(
                event.getId(),
                event.getSlug(),
                event.getName(),
                event.getVenue(),
                event.getDescription(),
                event.getPerformanceAt(),
                event.getSalesStartAt(),
                event.getStatus().name(),
                sortedTiers(event).stream()
                        .map(this::toTierSummary)
                        .toList()
        );
    }

    private EventDetailResponse toDetail(EventEntity event) {
        return new EventDetailResponse(
                event.getId(),
                event.getSlug(),
                event.getName(),
                event.getVenue(),
                event.getDescription(),
                event.getPerformanceAt(),
                event.getSalesStartAt(),
                event.getStatus().name(),
                sortedTiers(event).stream()
                        .map(this::toTierDetail)
                        .toList()
        );
    }

    private List<TicketTierEntity> sortedTiers(EventEntity event) {
        return event.getTicketTiers().stream()
                .sorted(Comparator.comparing(TicketTierEntity::getPrice).reversed())
                .toList();
    }

    private TicketTierSummaryResponse toTierSummary(TicketTierEntity tier) {
        var stock = inventoryMapper.toStockSnapshot(tier);
        return new TicketTierSummaryResponse(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getPrice(),
                tier.getTotalStock(),
                stock.availableStock()
        );
    }

    private TicketTierDetailResponse toTierDetail(TicketTierEntity tier) {
        var stock = inventoryMapper.toStockSnapshot(tier);
        return new TicketTierDetailResponse(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getPrice(),
                tier.getTotalStock(),
                stock.availableStock(),
                stock.reservedStock(),
                stock.soldStock()
        );
    }
}

