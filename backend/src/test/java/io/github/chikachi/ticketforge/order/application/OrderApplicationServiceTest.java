package io.github.chikachi.ticketforge.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.EventStatus;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.event.infrastructure.TicketTierRepository;
import io.github.chikachi.ticketforge.inventory.domain.TicketInventoryEntity;
import io.github.chikachi.ticketforge.inventory.infrastructure.TicketInventoryRepository;
import io.github.chikachi.ticketforge.observability.TicketForgeMetrics;
import io.github.chikachi.ticketforge.order.api.CreateOrderRequest;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.user.domain.AppUser;
import io.github.chikachi.ticketforge.user.domain.AppUserRole;
import io.github.chikachi.ticketforge.user.infrastructure.AppUserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TicketTierRepository ticketTierRepository;

    @Mock
    private TicketInventoryRepository ticketInventoryRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private TicketForgeMetrics metrics;

    private OrderApplicationService service;

    private AppUser user;
    private TicketTierEntity ticketTier;

    @BeforeEach
    void setUp() {
        OrderProperties properties = new OrderProperties();
        properties.setReservationTtl(Duration.ofMinutes(5));
        user = new AppUser(1L, "user@ticketforge.local", "TicketForge Demo User", AppUserRole.USER);
        ticketTier = ticketTier(EventStatus.ON_SALE, Instant.parse("2025-01-01T00:00:00Z"), 100);
        service = new OrderApplicationService(
                appUserRepository,
                ticketTierRepository,
                ticketInventoryRepository,
                ticketOrderRepository,
                orderNumberGenerator,
                properties,
                new OrderMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                metrics
        );
        lenient().when(metrics.startTimer()).thenReturn(io.micrometer.core.instrument.Timer.start(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void createsPendingOrderAndReservesInventory() {
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByUserIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty());
        when(ticketTierRepository.findDetailedById(1L)).thenReturn(Optional.of(ticketTier));
        when(ticketInventoryRepository.reserve(1L, 2)).thenReturn(1);
        when(orderNumberGenerator.generate()).thenReturn("TF-20260616-ABC123");
        when(ticketOrderRepository.saveAndFlush(any(TicketOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createOrder(user.getEmail(), "key-1", new CreateOrderRequest(1L, 2));

        assertThat(result.created()).isTrue();
        assertThat(result.response().orderNumber()).isEqualTo("TF-20260616-ABC123");
        assertThat(result.response().status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.response().unitPrice()).isEqualByComparingTo("1280.00");
        assertThat(result.response().totalAmount()).isEqualByComparingTo("2560.00");
        assertThat(result.response().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(result.response().idempotentReplay()).isFalse();
        verify(ticketInventoryRepository).reserve(1L, 2);
    }

    @Test
    void rejectsOutOfStockWithoutSavingOrder() {
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByUserIdAndIdempotencyKey(1L, "key-2")).thenReturn(Optional.empty());
        when(ticketTierRepository.findDetailedById(1L)).thenReturn(Optional.of(ticketTier));
        when(ticketInventoryRepository.reserve(1L, 6)).thenReturn(0);

        assertThatThrownBy(() -> service.createOrder(user.getEmail(), "key-2", new CreateOrderRequest(1L, 6)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not enough");
        verify(ticketOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThatThrownBy(() -> service.createOrder(user.getEmail(), "key-3", new CreateOrderRequest(1L, 0)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("quantity");
        verify(appUserRepository, never()).findByEmailForUpdate(any());
    }

    @Test
    void rejectsEventBeforeSalesStart() {
        ticketTier = ticketTier(EventStatus.ON_SALE, NOW.plusSeconds(60), 100);
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByUserIdAndIdempotencyKey(1L, "key-4")).thenReturn(Optional.empty());
        when(ticketTierRepository.findDetailedById(1L)).thenReturn(Optional.of(ticketTier));

        assertThatThrownBy(() -> service.createOrder(user.getEmail(), "key-4", new CreateOrderRequest(1L, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not on sale");
        verify(ticketInventoryRepository, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void idempotentReplayReturnsExistingOrderWithoutReservingAgain() {
        TicketOrder existingOrder = pendingOrder("TF-20260616-EXISTING", "same-key", 1);
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByUserIdAndIdempotencyKey(1L, "same-key")).thenReturn(Optional.of(existingOrder));

        var result = service.createOrder(user.getEmail(), "same-key", new CreateOrderRequest(1L, 1));

        assertThat(result.created()).isFalse();
        assertThat(result.response().orderNumber()).isEqualTo("TF-20260616-EXISTING");
        assertThat(result.response().idempotentReplay()).isTrue();
        verify(ticketInventoryRepository, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void activeCancelRestoresInventoryOnce() {
        TicketOrder order = pendingOrder("TF-20260616-CANCEL", "cancel-key", 3);
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate(order.getOrderNumber(), 1L)).thenReturn(Optional.of(order));
        when(ticketInventoryRepository.release(1L, 3)).thenReturn(1);

        var response = service.cancelOrder(user.getEmail(), order.getOrderNumber());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelledAt()).isEqualTo(NOW);
        verify(ticketInventoryRepository).release(1L, 3);
    }

    @Test
    void repeatedCancelDoesNotRestoreInventoryAgain() {
        TicketOrder order = pendingOrder("TF-20260616-CANCELLED", "cancelled-key", 1);
        order.cancel(NOW.minusSeconds(10));
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate(order.getOrderNumber(), 1L)).thenReturn(Optional.of(order));

        var response = service.cancelOrder(user.getEmail(), order.getOrderNumber());

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(ticketInventoryRepository, never()).release(anyLong(), anyInt());
    }

    @Test
    void paidOrderCannotBeCancelled() {
        TicketOrder order = pendingOrder("TF-20260616-PAID", "paid-key", 1);
        order.markPaid(NOW.minusSeconds(10));
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate(order.getOrderNumber(), 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(user.getEmail(), order.getOrderNumber()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be cancelled");
        verify(ticketInventoryRepository, never()).release(anyLong(), anyInt());
    }

    @Test
    void expiresPendingOrderAndRestoresInventory() {
        TicketOrder order = pendingOrder("TF-20260616-EXPIRE", "expire-key", 1);
        when(ticketOrderRepository.findDetailedPendingByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(ticketInventoryRepository.release(1L, 1)).thenReturn(1);

        service.expireOrder(order.getOrderNumber());

        assertThat(order.getStatus().name()).isEqualTo("CANCELLED");
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        verify(ticketInventoryRepository).release(1L, 1);
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserId("missing", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(user.getEmail(), "missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void unknownUserReturnsNotFound() {
        when(appUserRepository.findByEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder("missing@example.com", "key", new CreateOrderRequest(1L, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    private TicketOrder pendingOrder(String orderNumber, String idempotencyKey, int quantity) {
        return new TicketOrder(
                orderNumber,
                user,
                ticketTier.getEvent(),
                ticketTier,
                quantity,
                ticketTier.getPrice(),
                ticketTier.getPrice().multiply(BigDecimal.valueOf(quantity)),
                idempotencyKey,
                NOW.plus(Duration.ofMinutes(5)),
                NOW
        );
    }

    private static TicketTierEntity ticketTier(EventStatus status, Instant salesStartAt, int totalStock) {
        EventEntity event = new EventEntity(
                1L,
                "ticketforge-opening-live",
                "TicketForge Opening Live",
                "Yokohama Arena",
                "Demo event",
                Instant.parse("2026-09-20T10:00:00Z"),
                salesStartAt,
                status
        );
        TicketTierEntity tier = new TicketTierEntity(1L, "VIP", "VIP", new BigDecimal("1280.00"), totalStock);
        tier.attachInventory(new TicketInventoryEntity(1L, totalStock, 0, 0, 0));
        event.addTicketTier(tier);
        return tier;
    }
}
