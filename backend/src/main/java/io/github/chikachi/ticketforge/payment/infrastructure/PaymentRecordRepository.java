package io.github.chikachi.ticketforge.payment.infrastructure;

import io.github.chikachi.ticketforge.payment.domain.PaymentRecord;
import io.github.chikachi.ticketforge.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<PaymentRecord> findByProviderEventId(String providerEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentRecord> findByPaymentTransactionId(String paymentTransactionId);

    @Query("""
            select p
            from PaymentRecord p
            join fetch p.order
            where p.paymentTransactionId = :paymentTransactionId
            """)
    Optional<PaymentRecord> findDetailedByPaymentTransactionId(String paymentTransactionId);

    @Query("""
            select p
            from PaymentRecord p
            join fetch p.order o
            join fetch o.user
            where p.paymentTransactionId = :paymentTransactionId
              and o.user.email = :userEmail
            """)
    Optional<PaymentRecord> findDetailedByPaymentTransactionIdAndUserEmail(String paymentTransactionId, String userEmail);
}
