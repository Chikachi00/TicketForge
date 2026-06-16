package io.github.chikachi.ticketforge.demo.api;

public record DemoProfileResponse(
        boolean enabled,
        String profile,
        String eventSlug
) {
}
