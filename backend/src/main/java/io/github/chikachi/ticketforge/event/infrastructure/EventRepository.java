package io.github.chikachi.ticketforge.event.infrastructure;

import io.github.chikachi.ticketforge.event.domain.EventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<EventEntity, Long> {

    @Query("""
            select distinct e
            from EventEntity e
            left join fetch e.ticketTiers t
            left join fetch t.inventory
            order by e.performanceAt asc
            """)
    List<EventEntity> findAllWithTicketTiers();

    @Query("""
            select distinct e
            from EventEntity e
            left join fetch e.ticketTiers t
            left join fetch t.inventory
            where e.id = :eventId
            """)
    Optional<EventEntity> findDetailedById(Long eventId);

    @Query("""
            select distinct e
            from EventEntity e
            left join fetch e.ticketTiers t
            left join fetch t.inventory
            where e.slug = :slug
            """)
    Optional<EventEntity> findDetailedBySlug(String slug);
}

