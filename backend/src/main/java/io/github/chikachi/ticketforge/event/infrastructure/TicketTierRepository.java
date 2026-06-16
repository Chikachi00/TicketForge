package io.github.chikachi.ticketforge.event.infrastructure;

import io.github.chikachi.ticketforge.event.domain.TicketTierEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketTierRepository extends JpaRepository<TicketTierEntity, Long> {

    @Query("""
            select t
            from TicketTierEntity t
            join fetch t.event e
            left join fetch t.inventory
            where t.id = :ticketTierId
            """)
    Optional<TicketTierEntity> findDetailedById(Long ticketTierId);
}
