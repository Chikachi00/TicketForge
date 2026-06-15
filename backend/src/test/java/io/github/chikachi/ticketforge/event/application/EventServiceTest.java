package io.github.chikachi.ticketforge.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.EventStatus;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.event.infrastructure.EventRepository;
import io.github.chikachi.ticketforge.inventory.application.InventoryMapper;
import io.github.chikachi.ticketforge.inventory.domain.TicketInventoryEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, new InventoryMapper());
    }

    @Test
    void listEventsReturnsEventAndTicketTierSummaries() {
        EventEntity event = demoEvent();
        when(eventRepository.findAllWithTicketTiers()).thenReturn(List.of(event));

        var events = eventService.listEvents();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().slug()).isEqualTo("ticketforge-opening-live");
        assertThat(events.getFirst().ticketTiers()).extracting("code").containsExactly("VIP", "S", "A");
        assertThat(events.getFirst().ticketTiers().getFirst().availableStock()).isEqualTo(100);
    }

    @Test
    void getEventReturnsDetailedInventory() {
        when(eventRepository.findDetailedById(1L)).thenReturn(Optional.of(demoEvent()));

        var event = eventService.getEvent(1L);

        assertThat(event.ticketTiers()).hasSize(3);
        assertThat(event.ticketTiers().getFirst().price()).isEqualByComparingTo("1280.00");
        assertThat(event.ticketTiers().getFirst().reservedStock()).isZero();
        assertThat(event.ticketTiers().getFirst().soldStock()).isZero();
    }

    @Test
    void getEventThrowsWhenMissing() {
        when(eventRepository.findDetailedById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    static EventEntity demoEvent() {
        EventEntity event = new EventEntity(
                1L,
                "ticketforge-opening-live",
                "TicketForge Opening Live",
                "Yokohama Arena",
                "Demo event",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z"),
                EventStatus.ON_SALE
        );
        event.addTicketTier(tier(1L, "VIP", "VIP", "1280.00", 100));
        event.addTicketTier(tier(2L, "S", "S", "880.00", 500));
        event.addTicketTier(tier(3L, "A", "A", "580.00", 1000));
        return event;
    }

    private static TicketTierEntity tier(Long id, String code, String name, String price, int totalStock) {
        TicketTierEntity tier = new TicketTierEntity(id, code, name, new BigDecimal(price), totalStock);
        tier.attachInventory(new TicketInventoryEntity(id, totalStock, 0, 0, 0));
        return tier;
    }
}

