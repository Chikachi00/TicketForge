package io.github.chikachi.ticketforge.order.application;

import io.github.chikachi.ticketforge.order.api.OrderResponse;
import io.github.chikachi.ticketforge.order.api.OrderSummaryResponse;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(TicketOrder order, boolean idempotentReplay) {
        return new OrderResponse(
                order.getOrderNumber(),
                order.getEvent().getId(),
                order.getEvent().getName(),
                order.getTicketTier().getId(),
                order.getTicketTier().getCode(),
                order.getTicketTier().getName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getExpiresAt(),
                order.getCancelledAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                idempotentReplay
        );
    }

    public OrderSummaryResponse toSummary(TicketOrder order) {
        return new OrderSummaryResponse(
                order.getOrderNumber(),
                order.getEvent().getName(),
                order.getTicketTier().getCode(),
                order.getTicketTier().getName(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getExpiresAt(),
                order.getCancelledAt(),
                order.getPaidAt(),
                order.getCreatedAt()
        );
    }
}
