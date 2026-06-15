package io.github.chikachi.ticketforge.event.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class EventSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesMoneyAndIso8601UtcTime() throws Exception {
        EventDetailResponse response = new EventDetailResponse(
                1L,
                "ticketforge-opening-live",
                "TicketForge Opening Live",
                "Yokohama Arena",
                "Demo event",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z"),
                "ON_SALE",
                List.of(new TicketTierDetailResponse(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100, 100, 0, 0))
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"performanceAt\":\"2026-09-20T10:00:00Z\"");
        assertThat(json).contains("\"salesStartAt\":\"2026-07-01T01:00:00Z\"");
        assertThat(json).contains("\"price\":1280.00");
    }
}

