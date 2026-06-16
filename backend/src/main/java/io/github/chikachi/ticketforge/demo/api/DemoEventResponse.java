package io.github.chikachi.ticketforge.demo.api;

public record DemoEventResponse(
        Long id,
        String slug,
        String name,
        String status
) {
}
