package io.github.chikachi.ticketforge.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class TicketForgeMetrics {

    private final MeterRegistry registry;
    private final Timer orderReservationDuration;
    private final Timer paymentCallbackDuration;

    public TicketForgeMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.orderReservationDuration = Timer.builder("ticketforge_order_reservation_duration")
                .description("Order reservation transaction duration")
                .publishPercentileHistogram()
                .register(registry);
        this.paymentCallbackDuration = Timer.builder("ticketforge_payment_callback_duration")
                .description("Payment callback transaction duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void orderCreated(String ticketTierCode) {
        counter("ticketforge_orders_created_total", "ticket_tier_code", bounded(ticketTierCode)).increment();
    }

    public void orderIdempotentReplay() {
        counter("ticketforge_orders_idempotent_replay_total", "result", "replay").increment();
    }

    public void orderRejected(String reason) {
        counter("ticketforge_orders_rejected_total", "reason", bounded(reason)).increment();
    }

    public void inventoryReserved(String ticketTierCode, int quantity) {
        counter("ticketforge_inventory_reserved_total", "ticket_tier_code", bounded(ticketTierCode)).increment(quantity);
    }

    public void inventoryReleased(String ticketTierCode, int quantity) {
        counter("ticketforge_inventory_released_total", "ticket_tier_code", bounded(ticketTierCode)).increment(quantity);
    }

    public void paymentSucceeded() {
        counter("ticketforge_payments_success_total", "status", "SUCCESS").increment();
    }

    public void paymentFailed(String reason) {
        counter("ticketforge_payments_failed_total", "reason", bounded(reason)).increment();
    }

    public void paymentCallbackReplay() {
        counter("ticketforge_payment_callback_replay_total", "result", "replay").increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordOrderReservation(Timer.Sample sample) {
        sample.stop(orderReservationDuration);
    }

    public void recordPaymentCallback(Timer.Sample sample) {
        sample.stop(paymentCallbackDuration);
    }

    public void recordOrderReservation(Duration duration) {
        orderReservationDuration.record(duration);
    }

    public void recordPaymentCallback(Duration duration) {
        paymentCallbackDuration.record(duration);
    }

    private Counter counter(String name, String tagName, String tagValue) {
        return Counter.builder(name).tag(tagName, tagValue).register(registry);
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String safe = value.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }
}
