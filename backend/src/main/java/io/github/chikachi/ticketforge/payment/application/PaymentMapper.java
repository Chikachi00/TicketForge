package io.github.chikachi.ticketforge.payment.application;

import io.github.chikachi.ticketforge.payment.api.PaymentQueryResponse;
import io.github.chikachi.ticketforge.payment.api.PaymentSessionResponse;
import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentSessionResponse toSessionResponse(PaymentRecord payment) {
        return new PaymentSessionResponse(
                payment.getPaymentTransactionId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getCreatedAt()
        );
    }

    public PaymentQueryResponse toQueryResponse(PaymentRecord payment) {
        return new PaymentQueryResponse(
                payment.getPaymentTransactionId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getProvider(),
                payment.getCreatedAt(),
                payment.getProcessedAt()
        );
    }
}
