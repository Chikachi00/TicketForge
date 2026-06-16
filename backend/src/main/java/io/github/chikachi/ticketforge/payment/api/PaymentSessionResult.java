package io.github.chikachi.ticketforge.payment.api;

public record PaymentSessionResult(
        PaymentSessionResponse response,
        boolean created
) {
}
