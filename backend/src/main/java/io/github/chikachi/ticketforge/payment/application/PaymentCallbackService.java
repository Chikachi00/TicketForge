package io.github.chikachi.ticketforge.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.inventory.infrastructure.TicketInventoryRepository;
import io.github.chikachi.ticketforge.order.domain.OrderStatus;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.payment.api.PaymentCallbackRequest;
import io.github.chikachi.ticketforge.payment.api.PaymentCallbackResponse;
import io.github.chikachi.ticketforge.payment.domain.PaymentCallbackStatus;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.infrastructure.PaymentRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCallbackService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final PaymentSignatureService paymentSignatureService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentCallbackService(PaymentRecordRepository paymentRecordRepository,
                                  TicketOrderRepository ticketOrderRepository,
                                  TicketInventoryRepository ticketInventoryRepository,
                                  PaymentSignatureService paymentSignatureService,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.ticketOrderRepository = ticketOrderRepository;
        this.ticketInventoryRepository = ticketInventoryRepository;
        this.paymentSignatureService = paymentSignatureService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request, String signature) {
        if (!paymentSignatureService.verify(request, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_SIGNATURE", "Invalid payment callback signature");
        }

        PaymentCallbackStatus callbackStatus = parseStatus(request.status());
        Optional<PaymentRecord> existingProviderEvent = paymentRecordRepository.findByProviderEventId(request.providerEventId());
        if (existingProviderEvent.isPresent()) {
            return providerEventReplay(existingProviderEvent.get(), request, callbackStatus);
        }

        PaymentRecord payment = paymentRecordRepository.findByPaymentTransactionId(request.paymentTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment not found: " + request.paymentTransactionId()));

        if (!payment.isPending()) {
            return replayProcessedPayment(payment, request, callbackStatus);
        }

        TicketOrder order = ticketOrderRepository.findDetailedByOrderNumberForUpdate(request.orderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found: " + request.orderNumber()));
        ensureCallbackMatchesPayment(payment, order, request);

        Instant now = Instant.now(clock);
        String callbackPayload = callbackPayload(request);

        if (callbackStatus == PaymentCallbackStatus.FAILED) {
            payment.markFailed(request.providerEventId(), callbackPayload, safeFailureReason(request.failureReason()), now);
            return response(payment, order, false);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            payment.markFailed(request.providerEventId(), callbackPayload, "ORDER_ALREADY_CANCELLED", now);
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_CANCELLED", "Cancelled order cannot be paid");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order has already been paid");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SESSION_NOT_ALLOWED", "Order is not payable");
        }

        int updatedRows = ticketInventoryRepository.sellReserved(order.getTicketTier().getId(), order.getQuantity());
        if (updatedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_CONSISTENCY_ERROR", "Reserved inventory could not be converted to sold stock");
        }
        order.markPaid(now);
        payment.markSuccess(request.providerEventId(), callbackPayload, now);
        return response(payment, order, false);
    }

    private PaymentCallbackStatus parseStatus(String status) {
        try {
            return PaymentCallbackStatus.valueOf(status);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_STATUS", "Unsupported payment callback status: " + status);
        }
    }

    private PaymentCallbackResponse providerEventReplay(PaymentRecord existing,
                                                        PaymentCallbackRequest request,
                                                        PaymentCallbackStatus callbackStatus) {
        if (existing.getPaymentTransactionId().equals(request.paymentTransactionId())
                && existing.getStatus().name().equals(callbackStatus.name())) {
            return response(existing, existing.getOrder(), true);
        }
        throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED", "Provider event has already been processed");
    }

    private PaymentCallbackResponse replayProcessedPayment(PaymentRecord payment,
                                                           PaymentCallbackRequest request,
                                                           PaymentCallbackStatus callbackStatus) {
        ensureProcessedReplayMatches(payment, request, callbackStatus);
        return response(payment, payment.getOrder(), true);
    }

    private void ensureProcessedReplayMatches(PaymentRecord payment,
                                              PaymentCallbackRequest request,
                                              PaymentCallbackStatus callbackStatus) {
        if (!payment.getPaymentTransactionId().equals(request.paymentTransactionId())
                || !payment.getOrder().getOrderNumber().equals(request.orderNumber())
                || !payment.getStatus().name().equals(callbackStatus.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED", "Payment has already been processed");
        }
        ensureAmount(payment.getAmount(), request.amount());
        ensureCurrency(payment.getCurrency(), request.currency());
    }

    private void ensureCallbackMatchesPayment(PaymentRecord payment, TicketOrder order, PaymentCallbackRequest request) {
        if (!payment.getOrder().getId().equals(order.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SESSION_NOT_ALLOWED", "Payment does not belong to the callback order");
        }
        ensureAmount(order.getTotalAmount(), request.amount());
        ensureAmount(payment.getAmount(), request.amount());
        ensureCurrency(payment.getCurrency(), request.currency());
    }

    private void ensureAmount(BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match the order");
        }
    }

    private void ensureCurrency(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CURRENCY_MISMATCH", "Payment currency does not match the order");
        }
    }

    private String callbackPayload(PaymentCallbackRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            return paymentSignatureService.signingString(request);
        }
    }

    private String safeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "PROVIDER_REPORTED_FAILURE";
        }
        String safe = failureReason.trim().replaceAll("[^A-Z0-9_\\-]", "_");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private PaymentCallbackResponse response(PaymentRecord payment, TicketOrder order, boolean replay) {
        return new PaymentCallbackResponse(
                payment.getProviderEventId(),
                payment.getPaymentTransactionId(),
                order.getOrderNumber(),
                payment.getStatus().name(),
                replay,
                payment.getProcessedAt()
        );
    }

}
