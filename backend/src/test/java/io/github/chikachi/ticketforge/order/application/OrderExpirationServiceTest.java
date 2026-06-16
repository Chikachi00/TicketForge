package io.github.chikachi.ticketforge.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.chikachi.ticketforge.order.infrastructure.TicketOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Test
    void findsExpiredPendingOrdersUsingFixedClockAndBatchLimit() {
        Instant now = Instant.parse("2026-06-16T10:05:00Z");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(ticketOrderRepository.findExpiredPendingOrderNumbers(eq(now), pageableCaptor.capture()))
                .thenReturn(List.of("TF-1"));

        OrderExpirationService service = new OrderExpirationService(
                ticketOrderRepository,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        List<String> orderNumbers = service.findExpiredPendingOrderNumbers();

        assertThat(orderNumbers).containsExactly("TF-1");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }
}
