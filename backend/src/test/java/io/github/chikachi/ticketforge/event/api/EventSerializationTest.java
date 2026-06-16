package io.github.chikachi.ticketforge.event.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.github.chikachi.ticketforge.payment.api.PaymentQueryResponse;
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

    @Test
    void serializesPaymentMoneyAndProcessedTimeInUtc() throws Exception {
        PaymentQueryResponse response = new PaymentQueryResponse(
                "PAY-1",
                "TF-1",
                new BigDecimal("1280.00"),
                "CNY",
                "SUCCESS",
                "TICKETFORGE_SIMULATOR",
                Instant.parse("2026-06-16T10:00:00Z"),
                Instant.parse("2026-06-16T10:02:00Z")
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"amount\":1280.00");
        assertThat(json).contains("\"processedAt\":\"2026-06-16T10:02:00Z\"");
    }
}
