package io.github.chikachi.ticketforge.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.EventStatus;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.domain.PaymentStatus;
import io.github.chikachi.ticketforge.payment.infrastructure.PaymentRecordRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    private PaymentApplicationService service;
    private AppUser user;
    private TicketOrder order;

    @BeforeEach
    void setUp() {
        user = new AppUser(1L, "user@ticketforge.local", "Demo User", AppUserRole.USER);
        order = pendingOrder("TF-1", 1);
        service = new PaymentApplicationService(
                appUserRepository,
                ticketOrderRepository,
                paymentRecordRepository,
                new PaymentMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsPaymentSessionForPendingOrder() {
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate("TF-1", 1L)).thenReturn(Optional.of(order));
        when(paymentRecordRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRecordRepository.saveAndFlush(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createPaymentSession(user.getEmail(), "pay-key", "TF-1");

        assertThat(result.created()).isTrue();
        assertThat(result.response().paymentTransactionId()).startsWith("PAY-20260616-");
        assertThat(result.response().amount()).isEqualByComparingTo("1280.00");
        assertThat(result.response().status()).isEqualTo("PENDING");
    }

    @Test
    void existingPendingPaymentReturnsOriginalSession() {
        PaymentRecord existing = new PaymentRecord(order, "PAY-EXISTING", order.getTotalAmount(), NOW.minusSeconds(30));
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate("TF-1", 1L)).thenReturn(Optional.of(order));
        when(paymentRecordRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING)).thenReturn(Optional.of(existing));

        var result = service.createPaymentSession(user.getEmail(), "pay-key", "TF-1");

        assertThat(result.created()).isFalse();
        assertThat(result.response().paymentTransactionId()).isEqualTo("PAY-EXISTING");
        verify(paymentRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonPendingOrderCannotCreatePaymentSession() {
        order.markPaid(NOW);
        when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate("TF-1", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createPaymentSession(user.getEmail(), "pay-key", "TF-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void paymentQueryIsScopedToUserEmail() {
        when(paymentRecordRepository.findDetailedByPaymentTransactionIdAndUserEmail("PAY-1", "other@ticketforge.local"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment("other@ticketforge.local", "PAY-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PAY-1");
    }

    private TicketOrder pendingOrder(String orderNumber, int quantity) {
        EventEntity event = new EventEntity(1L, "event", "Event", "Venue", "Demo",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"), EventStatus.ON_SALE);
        TicketTierEntity tier = new TicketTierEntity(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100);
        event.addTicketTier(tier);
        TicketOrder ticketOrder = new TicketOrder(orderNumber, user, event, tier, quantity,
                tier.getPrice(), tier.getPrice().multiply(BigDecimal.valueOf(quantity)),
                "order-key", NOW.plus(Duration.ofMinutes(5)), NOW);
        ReflectionTestUtils.setField(ticketOrder, "id", 1L);
        return ticketOrder;
    }
}
