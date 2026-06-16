package io.github.chikachi.ticketforge.payment.application;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.order.domain.OrderStatus;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import io.github.chikachi.ticketforge.payment.api.PaymentQueryResponse;
import io.github.chikachi.ticketforge.payment.api.PaymentSessionResult;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.domain.PaymentStatus;
import io.github.chikachi.ticketforge.payment.infrastructure.PaymentRecordRepository;
import io.github.chikachi.ticketforge.user.domain.AppUser;
import io.github.chikachi.ticketforge.user.infrastructure.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApplicationService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;
    private static final DateTimeFormatter TRANSACTION_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final AppUserRepository appUserRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    public PaymentApplicationService(AppUserRepository appUserRepository,
                                     TicketOrderRepository ticketOrderRepository,
                                     PaymentRecordRepository paymentRecordRepository,
                                     PaymentMapper paymentMapper,
                                     Clock clock) {
        this.appUserRepository = appUserRepository;
        this.ticketOrderRepository = ticketOrderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentMapper = paymentMapper;
        this.clock = clock;
    }

    @Transactional
    public PaymentSessionResult createPaymentSession(String userEmail, String idempotencyKey, String orderNumber) {
        validateIdempotencyKey(idempotencyKey);
        AppUser user = appUserRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userEmail));
        TicketOrder order = ticketOrderRepository.findDetailedByOrderNumberAndUserIdForUpdate(orderNumber, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderNumber));

        ensurePaymentSessionAllowed(order);

        return paymentRecordRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                .map(payment -> new PaymentSessionResult(paymentMapper.toSessionResponse(payment), false))
                .orElseGet(() -> createNewSession(order));
    }

    @Transactional(readOnly = true)
    public PaymentQueryResponse getPayment(String userEmail, String paymentTransactionId) {
        return paymentRecordRepository.findDetailedByPaymentTransactionIdAndUserEmail(paymentTransactionId, userEmail)
                .map(paymentMapper::toQueryResponse)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment not found: " + paymentTransactionId));
    }

    private PaymentSessionResult createNewSession(TicketOrder order) {
        Instant now = Instant.now(clock);
        PaymentRecord payment = new PaymentRecord(order, generateTransactionId(now), order.getTotalAmount(), now);
        PaymentRecord saved = paymentRecordRepository.saveAndFlush(payment);
        return new PaymentSessionResult(paymentMapper.toSessionResponse(saved), true);
    }

    private String generateTransactionId(Instant now) {
        return "PAY-" + TRANSACTION_DATE.format(now) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private void ensurePaymentSessionAllowed(TicketOrder order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SESSION_NOT_ALLOWED", "Payment session can only be created for pending orders");
        }
        if (!order.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SESSION_NOT_ALLOWED", "Order has expired");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be present and at most 120 characters");
        }
    }
}
