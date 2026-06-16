package io.github.chikachi.ticketforge.order.application;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.domain.EventStatus;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.event.infrastructure.TicketTierRepository;
import io.github.chikachi.ticketforge.inventory.infrastructure.TicketInventoryRepository;
import io.github.chikachi.ticketforge.observability.TicketForgeMetrics;
import io.github.chikachi.ticketforge.order.api.CreateOrderRequest;
import io.github.chikachi.ticketforge.order.api.CreateOrderResult;
import io.github.chikachi.ticketforge.order.api.OrderResponse;
import io.github.chikachi.ticketforge.order.api.OrderSummaryResponse;
import io.github.chikachi.ticketforge.order.domain.OrderStatus;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.user.domain.AppUser;
import io.github.chikachi.ticketforge.user.infrastructure.AppUserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Timer;

@Service
public class OrderApplicationService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;

    private final AppUserRepository appUserRepository;
    private final TicketTierRepository ticketTierRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderProperties orderProperties;
    private final OrderMapper orderMapper;
    private final Clock clock;
    private final TicketForgeMetrics metrics;

    public OrderApplicationService(AppUserRepository appUserRepository,
                                   TicketTierRepository ticketTierRepository,
                                   TicketInventoryRepository ticketInventoryRepository,
                                   TicketOrderRepository ticketOrderRepository,
                                   OrderNumberGenerator orderNumberGenerator,
                                   OrderProperties orderProperties,
                                   OrderMapper orderMapper,
                                   Clock clock,
                                   TicketForgeMetrics metrics) {
        this.appUserRepository = appUserRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.ticketInventoryRepository = ticketInventoryRepository;
        this.ticketOrderRepository = ticketOrderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderProperties = orderProperties;
        this.orderMapper = orderMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public CreateOrderResult createOrder(String userEmail, String idempotencyKey, CreateOrderRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String normalizedKey = validateIdempotencyKey(idempotencyKey);
        validateRequest(request);
        AppUser user = lockUser(userEmail);

        try {
            return ticketOrderRepository.findDetailedByUserIdAndIdempotencyKey(user.getId(), normalizedKey)
                    .map(order -> {
                        metrics.orderIdempotentReplay();
                        return new CreateOrderResult(orderMapper.toResponse(order, true), false);
                    })
                    .orElseGet(() -> createNewOrder(user, normalizedKey, request));
        } catch (ApiException exception) {
            metrics.orderRejected(exception.code());
            throw exception;
        } finally {
            metrics.recordOrderReservation(sample);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String userEmail, String orderNumber) {
        AppUser user = findUser(userEmail);
        TicketOrder order = ticketOrderRepository.findDetailedByOrderNumberAndUserId(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderNumber));
        return orderMapper.toResponse(order, false);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listMyOrders(String userEmail, OrderStatus status) {
        AppUser user = findUser(userEmail);
        List<TicketOrder> orders = status == null
                ? ticketOrderRepository.findDetailedByUserIdOrderByCreatedAtDesc(user.getId())
                : ticketOrderRepository.findDetailedByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status);
        return orders.stream().map(orderMapper::toSummary).toList();
    }

    @Transactional
    public OrderResponse cancelOrder(String userEmail, String orderNumber) {
        AppUser user = findUser(userEmail);
        TicketOrder order = ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderNumber));
        cancelLockedOrder(order);
        return orderMapper.toResponse(order, false);
    }

    @Transactional
    public void expireOrder(String orderNumber) {
        TicketOrder order = ticketOrderRepository.findDetailedPendingByOrderNumberForUpdate(orderNumber)
                .orElse(null);
        if (order != null) {
            cancelLockedOrder(order);
        }
    }

    private CreateOrderResult createNewOrder(AppUser user, String idempotencyKey, CreateOrderRequest request) {
        Instant now = Instant.now(clock);
        TicketTierEntity ticketTier = ticketTierRepository.findDetailedById(request.ticketTierId())
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_TIER_NOT_FOUND", "Ticket tier not found: " + request.ticketTierId()));

        ensureOnSale(ticketTier, now);
        reserveInventory(ticketTier.getId(), request.quantity());
        metrics.inventoryReserved(ticketTier.getCode(), request.quantity());

        BigDecimal unitPrice = ticketTier.getPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        TicketOrder order = new TicketOrder(
                orderNumberGenerator.generate(),
                user,
                ticketTier.getEvent(),
                ticketTier,
                request.quantity(),
                unitPrice,
                totalAmount,
                idempotencyKey,
                now.plus(orderProperties.getReservationTtl()),
                now
        );

        try {
            TicketOrder saved = ticketOrderRepository.saveAndFlush(order);
            metrics.orderCreated(ticketTier.getCode());
            return new CreateOrderResult(orderMapper.toResponse(saved, false), true);
        } catch (DataIntegrityViolationException exception) {
            metrics.orderRejected("INVENTORY_CONSISTENCY_ERROR");
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_CONSISTENCY_ERROR", "Order could not be created safely");
        }
    }

    private void ensureOnSale(TicketTierEntity ticketTier, Instant now) {
        if (ticketTier.getEvent().getStatus() != EventStatus.ON_SALE || now.isBefore(ticketTier.getEvent().getSalesStartAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_NOT_ON_SALE", "Event is not on sale");
        }
    }

    private void reserveInventory(Long ticketTierId, int quantity) {
        // PostgreSQL performs the concurrency control here; the affected row count is the stock gate.
        int updatedRows = ticketInventoryRepository.reserve(ticketTierId, quantity);
        if (updatedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "Not enough tickets are available");
        }
    }

    private void cancelLockedOrder(TicketOrder order) {
        if (order.isCancelled()) {
            return;
        }
        if (!order.isCancellable()) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Order cannot be cancelled");
        }

        int updatedRows = ticketInventoryRepository.release(order.getTicketTier().getId(), order.getQuantity());
        if (updatedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_CONSISTENCY_ERROR", "Reserved inventory could not be released");
        }
        metrics.inventoryReleased(order.getTicketTier().getCode(), order.getQuantity());
        order.cancel(Instant.now(clock));
    }

    private AppUser lockUser(String userEmail) {
        return appUserRepository.findByEmailForUpdate(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userEmail));
    }

    private AppUser findUser(String userEmail) {
        return appUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userEmail));
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be present and at most 120 characters");
        }
        return idempotencyKey.trim();
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request.ticketTierId() == null || request.quantity() < 1 || request.quantity() > 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ticketTierId is required and quantity must be between 1 and 6");
        }
    }
}
