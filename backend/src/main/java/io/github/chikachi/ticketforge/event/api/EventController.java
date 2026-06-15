package io.github.chikachi.ticketforge.event.api;

import io.github.chikachi.ticketforge.event.application.EventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventSummaryResponse> listEvents() {
        return eventService.listEvents();
    }

    @GetMapping("/{eventId}")
    public EventDetailResponse getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping("/slug/{slug}")
    public EventDetailResponse getEventBySlug(@PathVariable String slug) {
        return eventService.getEventBySlug(slug);
    }
}

