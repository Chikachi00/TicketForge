package io.github.chikachi.ticketforge.order.infrastructure;

import io.github.chikachi.ticketforge.order.domain.OrderStatus;
import io.github.chikachi.ticketforge.order.domain.TicketOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.user.id = :userId
              and o.idempotencyKey = :idempotencyKey
            """)
    Optional<TicketOrder> findDetailedByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.orderNumber = :orderNumber
              and o.user.id = :userId
            """)
    Optional<TicketOrder> findDetailedByOrderNumberAndUserId(String orderNumber, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.orderNumber = :orderNumber
              and o.user.id = :userId
            """)
    Optional<TicketOrder> findDetailedByOrderNumberAndUserIdForUpdate(String orderNumber, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from TicketOrder o
            join fetch o.user
            join fetch o.event
            join fetch o.ticketTier
            where o.orderNumber = :orderNumber
            """)
    Optional<TicketOrder> findDetailedByOrderNumberForUpdate(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.orderNumber = :orderNumber
              and o.status = io.github.chikachi.ticketforge.order.domain.OrderStatus.PENDING_PAYMENT
            """)
    Optional<TicketOrder> findDetailedPendingByOrderNumberForUpdate(String orderNumber);

    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.user.id = :userId
            order by o.createdAt desc
            """)
    List<TicketOrder> findDetailedByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select o
            from TicketOrder o
            join fetch o.event
            join fetch o.ticketTier
            where o.user.id = :userId
              and o.status = :status
            order by o.createdAt desc
            """)
    List<TicketOrder> findDetailedByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    @Query("""
            select o.orderNumber
            from TicketOrder o
            where o.status = io.github.chikachi.ticketforge.order.domain.OrderStatus.PENDING_PAYMENT
              and o.expiresAt <= :now
            order by o.expiresAt asc
            """)
    List<String> findExpiredPendingOrderNumbers(Instant now, Pageable pageable);
}
