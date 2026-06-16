package io.github.chikachi.ticketforge.order.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ticketforge.orders")
public class OrderProperties {

    private Duration reservationTtl = Duration.ofMinutes(5);
    private long expirationScanDelayMs = 10_000L;

    public Duration getReservationTtl() {
        return reservationTtl;
    }

    public void setReservationTtl(Duration reservationTtl) {
        this.reservationTtl = reservationTtl;
    }

    public long getExpirationScanDelayMs() {
        return expirationScanDelayMs;
    }

    public void setExpirationScanDelayMs(long expirationScanDelayMs) {
        this.expirationScanDelayMs = expirationScanDelayMs;
    }
}
