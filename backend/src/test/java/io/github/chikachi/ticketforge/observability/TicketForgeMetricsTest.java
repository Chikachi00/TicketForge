package io.github.chikachi.ticketforge.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TicketForgeMetricsTest {

    @Test
    void businessMetricsUseOnlyLowCardinalityTagKeys() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TicketForgeMetrics metrics = new TicketForgeMetrics(registry);

        metrics.orderCreated("LOAD");
        metrics.orderRejected("OUT_OF_STOCK");
        metrics.paymentFailed("SIMULATED_PAYMENT_DECLINED");
        metrics.paymentCallbackReplay();

        Set<String> forbidden = Set.of("orderNumber", "paymentTransactionId", "email", "idempotencyKey", "providerEventId", "userId");
        assertThat(registry.getMeters())
                .flatExtracting(Meter::getId)
                .flatExtracting(Meter.Id::getTags)
                .allSatisfy(tag -> assertThat(forbidden).doesNotContain(tag.getKey()));
    }
}
