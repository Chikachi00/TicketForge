package io.github.chikachi.ticketforge.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCallbackRequest(
        @NotBlank String providerEventId,
        @NotBlank String paymentTransactionId,
        @NotBlank String orderNumber,
        @NotBlank String status,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant occurredAt,
        String failureReason
) {
}
