package io.github.chikachi.ticketforge.order.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.order.application.OrderApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderApplicationService orderApplicationService;

    @Test
    void createOrderReturnsCreatedForNewOrder() throws Exception {
        when(orderApplicationService.createOrder(eq("user@ticketforge.local"), eq("key-1"), any(CreateOrderRequest.class)))
                .thenReturn(new CreateOrderResult(response(false), true));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTierId\":1,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/TF-20260616-ABC123"))
                .andExpect(jsonPath("$.orderNumber").value("TF-20260616-ABC123"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));
    }

    @Test
    void createOrderReturnsOkForIdempotentReplay() throws Exception {
        when(orderApplicationService.createOrder(eq("user@ticketforge.local"), eq("key-1"), any(CreateOrderRequest.class)))
                .thenReturn(new CreateOrderResult(response(true), false));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTierId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    @Test
    void invalidQuantityReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTierId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void outOfStockReturnsConflict() throws Exception {
        when(orderApplicationService.createOrder(eq("user@ticketforge.local"), eq("key-1"), any(CreateOrderRequest.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "Not enough tickets are available"));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTierId\":1,\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    void missingOrderReturns404() throws Exception {
        when(orderApplicationService.getOrder("user@ticketforge.local", "missing"))
                .thenThrow(new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found: missing"));

        mockMvc.perform(get("/api/orders/missing").header("X-User-Email", "user@ticketforge.local"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void listMyOrdersReturnsSummaries() throws Exception {
        when(orderApplicationService.listMyOrders("user@ticketforge.local", null))
                .thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/orders/me").header("X-User-Email", "user@ticketforge.local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].orderNumber").value("TF-20260616-ABC123"));
    }

    @Test
    void cancelOrderReturnsCancelledOrder() throws Exception {
        OrderResponse cancelled = new OrderResponse(
                "TF-20260616-ABC123",
                1L,
                "TicketForge Opening Live",
                1L,
                "VIP",
                "VIP",
                1,
                new BigDecimal("1280.00"),
                new BigDecimal("1280.00"),
                "CANCELLED",
                Instant.parse("2026-06-16T10:05:00Z"),
                Instant.parse("2026-06-16T10:02:00Z"),
                null,
                Instant.parse("2026-06-16T10:00:00Z"),
                false
        );
        when(orderApplicationService.cancelOrder("user@ticketforge.local", "TF-20260616-ABC123")).thenReturn(cancelled);

        mockMvc.perform(post("/api/orders/TF-20260616-ABC123/cancel").header("X-User-Email", "user@ticketforge.local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-06-16T10:02:00Z"));
    }

    private static OrderResponse response(boolean replay) {
        return new OrderResponse(
                "TF-20260616-ABC123",
                1L,
                "TicketForge Opening Live",
                1L,
                "VIP",
                "VIP",
                1,
                new BigDecimal("1280.00"),
                new BigDecimal("1280.00"),
                "PENDING_PAYMENT",
                Instant.parse("2026-06-16T10:05:00Z"),
                null,
                null,
                Instant.parse("2026-06-16T10:00:00Z"),
                replay
        );
    }

    private static OrderSummaryResponse summary() {
        return new OrderSummaryResponse(
                "TF-20260616-ABC123",
                "TicketForge Opening Live",
                "VIP",
                "VIP",
                1,
                new BigDecimal("1280.00"),
                "PENDING_PAYMENT",
                Instant.parse("2026-06-16T10:05:00Z"),
                null,
                null,
                Instant.parse("2026-06-16T10:00:00Z")
        );
    }
}
