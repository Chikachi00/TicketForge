package io.github.chikachi.ticketforge.order.api;

import io.github.chikachi.ticketforge.order.application.OrderApplicationService;
import io.github.chikachi.ticketforge.order.domain.OrderStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResult result = orderApplicationService.createOrder(userEmail, idempotencyKey, request);
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/orders/" + result.response().orderNumber()))
                    .body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse getOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable String orderNumber
    ) {
        return orderApplicationService.getOrder(userEmail, orderNumber);
    }

    @GetMapping("/me")
    public List<OrderSummaryResponse> listMyOrders(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestParam(required = false) OrderStatus status
    ) {
        return orderApplicationService.listMyOrders(userEmail, status);
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancelOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable String orderNumber
    ) {
        return orderApplicationService.cancelOrder(userEmail, orderNumber);
    }
}
