package io.github.chikachi.ticketforge.order.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    public OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        String date = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DATE_FORMATTER);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return "TF-" + date + "-" + suffix;
    }
}
