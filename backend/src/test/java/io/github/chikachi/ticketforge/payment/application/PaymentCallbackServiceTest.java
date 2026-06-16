package io.github.chikachi.ticketforge.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.event.domain.EventEntity;
import io.github.chikachi.ticketforge.event.domain.EventStatus;
import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import io.github.chikachi.ticketforge.inventory.infrastructure.TicketInventoryRepository;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.payment.api.PaymentCallbackRequest;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.infrastructure.PaymentRecordRepository;
import io.github.chikachi.ticketforge.user.domain.AppUser;
import io.github.chikachi.ticketforge.user.domain.AppUserRole;
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
class PaymentCallbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-16T10:02:00Z");

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private TicketInventoryRepository ticketInventoryRepository;

    @Mock
    private PaymentSignatureService paymentSignatureService;

    private PaymentCallbackService service;
    private TicketOrder order;
    private PaymentRecord payment;

    @BeforeEach
    void setUp() {
        order = pendingOrder("TF-1", 2);
        payment = new PaymentRecord(order, "PAY-1", order.getTotalAmount(), NOW.minusSeconds(60));
        service = new PaymentCallbackService(
                paymentRecordRepository,
                ticketOrderRepository,
                ticketInventoryRepository,
                paymentSignatureService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void successCallbackMarksOrderPaidAndTransfersReservedStockToSold() {
        stubValidPendingCallback(successRequest(order.getTotalAmount(), "CNY"));
        when(ticketInventoryRepository.sellReserved(1L, 2)).thenReturn(1);

        var response = service.handleCallback(successRequest(order.getTotalAmount(), "CNY"), "sig");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.idempotentReplay()).isFalse();
        assertThat(order.getStatus().name()).isEqualTo("PAID");
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.isSuccessful()).isTrue();
        verify(ticketInventoryRepository).sellReserved(1L, 2);
    }

    @Test
    void duplicateSuccessCallbackDoesNotTransferStockTwice() {
        payment.markSuccess("EVT-1", "{}", NOW.minusSeconds(5));
        PaymentCallbackRequest request = successRequest(order.getTotalAmount(), "CNY");
        when(paymentSignatureService.verify(request, "sig")).thenReturn(true);
        when(paymentRecordRepository.findByProviderEventId("EVT-1")).thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaymentTransactionId("PAY-1")).thenReturn(Optional.of(payment));

        var response = service.handleCallback(request, "sig");

        assertThat(response.idempotentReplay()).isTrue();
        verify(ticketInventoryRepository, never()).sellReserved(anyLong(), anyInt());
    }

    @Test
    void failureCallbackDoesNotChangeOrderStatusOrReleaseStock() {
        PaymentCallbackRequest request = failedRequest("DECLINED");
        when(paymentSignatureService.verify(request, "sig")).thenReturn(true);
        when(paymentRecordRepository.findByProviderEventId("EVT-1")).thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaymentTransactionId("PAY-1")).thenReturn(Optional.of(payment));
        when(ticketOrderRepository.findDetailedByOrderNumberForUpdate("TF-1")).thenReturn(Optional.of(order));

        var response = service.handleCallback(request, "sig");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(order.getStatus().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(payment.isFailed()).isTrue();
        verify(ticketInventoryRepository, never()).release(anyLong(), anyInt());
        verify(ticketInventoryRepository, never()).sellReserved(anyLong(), anyInt());
    }

    @Test
    void amountMismatchIsRejected() {
        PaymentCallbackRequest request = successRequest(new BigDecimal("999.00"), "CNY");
        stubValidPendingCallback(request);

        assertThatThrownBy(() -> service.handleCallback(request, "sig"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("amount");
        verify(ticketInventoryRepository, never()).sellReserved(anyLong(), anyInt());
    }

    @Test
    void currencyMismatchIsRejected() {
        PaymentCallbackRequest request = successRequest(order.getTotalAmount(), "JPY");
        stubValidPendingCallback(request);

        assertThatThrownBy(() -> service.handleCallback(request, "sig"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void invalidSignatureIsRejectedBeforeLoadingPayment() {
        PaymentCallbackRequest request = successRequest(order.getTotalAmount(), "CNY");
        when(paymentSignatureService.verify(request, "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.handleCallback(request, "bad"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("signature");
        verify(paymentRecordRepository, never()).findByPaymentTransactionId(any());
    }

    @Test
    void cancelledOrderCannotBePaidSuccessfully() {
        order.cancel(NOW.minusSeconds(10));
        PaymentCallbackRequest request = successRequest(order.getTotalAmount(), "CNY");
        stubValidPendingCallback(request);

        assertThatThrownBy(() -> service.handleCallback(request, "sig"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cancelled");
        assertThat(payment.isFailed()).isTrue();
        verify(ticketInventoryRepository, never()).sellReserved(anyLong(), anyInt());
    }

    @Test
    void invalidCallbackStatusIsRejected() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "EVT-1", "PAY-1", "TF-1", "CHARGED",
                order.getTotalAmount(), "CNY", NOW, null);
        when(paymentSignatureService.verify(request, "sig")).thenReturn(true);

        assertThatThrownBy(() -> service.handleCallback(request, "sig"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported");
    }

    private void stubValidPendingCallback(PaymentCallbackRequest request) {
        when(paymentSignatureService.verify(request, "sig")).thenReturn(true);
        when(paymentRecordRepository.findByProviderEventId("EVT-1")).thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaymentTransactionId("PAY-1")).thenReturn(Optional.of(payment));
        when(ticketOrderRepository.findDetailedByOrderNumberForUpdate("TF-1")).thenReturn(Optional.of(order));
    }

    private PaymentCallbackRequest successRequest(BigDecimal amount, String currency) {
        return new PaymentCallbackRequest("EVT-1", "PAY-1", "TF-1", "SUCCESS", amount, currency, NOW, null);
    }

    private PaymentCallbackRequest failedRequest(String reason) {
        return new PaymentCallbackRequest("EVT-1", "PAY-1", "TF-1", "FAILED", order.getTotalAmount(), "CNY", NOW, reason);
    }

    private TicketOrder pendingOrder(String orderNumber, int quantity) {
        AppUser user = new AppUser(1L, "user@ticketforge.local", "Demo User", AppUserRole.USER);
        EventEntity event = new EventEntity(1L, "event", "Event", "Venue", "Demo",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"), EventStatus.ON_SALE);
        TicketTierEntity tier = new TicketTierEntity(1L, "VIP", "VIP", new BigDecimal("1280.00"), 100);
        event.addTicketTier(tier);
        TicketOrder ticketOrder = new TicketOrder(orderNumber, user, event, tier, quantity,
                tier.getPrice(), tier.getPrice().multiply(BigDecimal.valueOf(quantity)),
                "order-key", NOW.plus(Duration.ofMinutes(5)), NOW.minusSeconds(120));
        ReflectionTestUtils.setField(ticketOrder, "id", 1L);
        return ticketOrder;
    }
}
