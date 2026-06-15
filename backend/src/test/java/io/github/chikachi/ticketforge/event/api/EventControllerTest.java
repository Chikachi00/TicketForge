package io.github.chikachi.ticketforge.event.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.application.EventService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @Test
    void listEventsReturnsJson() throws Exception {
        when(eventService.listEvents()).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("TicketForge Opening Live"))
                .andExpect(jsonPath("$[0].ticketTiers[0].availableStock").value(100));
    }

    @Test
    void missingEventReturnsStandard404Json() throws Exception {
        when(eventService.getEvent(99L))
                .thenThrow(new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found: 99"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/events/99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private static EventSummaryResponse summary() {
        return new EventSummaryResponse(
                1L,
                "ticketforge-opening-live",
                "TicketForge Opening Live",
                "Yokohama Arena",
                "Demo event",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z"),
                "ON_SALE",
                List.of(new TicketTierSummaryResponse(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100, 100))
        );
    }
}

