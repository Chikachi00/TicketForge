package io.github.chikachi.ticketforge.order.application;

import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderExpirationService {

    private static final int BATCH_SIZE = 100;

    private final TicketOrderRepository ticketOrderRepository;
    private final Clock clock;

    public OrderExpirationService(TicketOrderRepository ticketOrderRepository, Clock clock) {
        this.ticketOrderRepository = ticketOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<String> findExpiredPendingOrderNumbers() {
        return ticketOrderRepository.findExpiredPendingOrderNumbers(Instant.now(clock), PageRequest.of(0, BATCH_SIZE));
    }
}
