package io.github.chikachi.ticketforge.demo.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import io.github.chikachi.ticketforge.demo.api.DemoDashboardResponse;
import io.github.chikachi.ticketforge.demo.api.DemoEventResponse;
import io.github.chikachi.ticketforge.demo.api.DemoOrderResponse;
import io.github.chikachi.ticketforge.demo.api.DemoOrderStatsResponse;
import io.github.chikachi.ticketforge.demo.api.DemoPaymentStatsResponse;
import io.github.chikachi.ticketforge.demo.api.DemoResetResponse;
import io.github.chikachi.ticketforge.demo.api.DemoTicketTierInventoryResponse;

class DemoApplicationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DemoApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DemoApplicationService(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void dashboardOnlyQueriesDemoEventAndCalculatesInventoryInvariant() {
        stubDashboardData(
                List.of(
                        new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 99, 0, 1, 100, true),
                        new DemoTicketTierInventoryResponse(2L, "S", "S", 500, 499, 1, 0, 500, true)
                ),
                new DemoOrderStatsResponse(2, 1, 1, 0, 0),
                new DemoPaymentStatsResponse(0, 1, 0),
                List.of()
        );

        DemoDashboardResponse response = service.dashboard();

        assertThat(response.event().slug()).isEqualTo("ticketforge-opening-live");
        assertThat(response.inventory().totalStock()).isEqualTo(600);
        assertThat(response.inventory().availableStock()).isEqualTo(598);
        assertThat(response.inventory().reservedStock()).isEqualTo(1);
        assertThat(response.inventory().soldStock()).isEqualTo(1);
        assertThat(response.inventory().inventoryConsistent()).isTrue();
        assertThat(response.orders().pendingPayment()).isEqualTo(1);
        assertThat(response.payments().success()).isEqualTo(1);
    }

    @Test
    void dashboardReportsInconsistentTierInventory() {
        stubDashboardData(
                List.of(new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 90, 5, 6, 101, false)),
                new DemoOrderStatsResponse(0, 0, 0, 0, 0),
                new DemoPaymentStatsResponse(0, 0, 0),
                List.of()
        );

        DemoDashboardResponse response = service.dashboard();

        assertThat(response.inventory().inventoryConsistent()).isFalse();
        assertThat(response.inventory().calculatedTotal()).isEqualTo(101);
    }

    @Test
    void dashboardReturnsOnlyRecentTenOrdersFromDemoEventQuery() {
        List<DemoOrderResponse> orders = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new DemoOrderResponse(
                        "TF-" + index,
                        "VIP",
                        "VIP",
                        1,
                        "PAID",
                        java.math.BigDecimal.TEN,
                        "PAY-" + index,
                        "SUCCESS",
                        Instant.parse("2026-06-16T10:00:00Z"),
                        Instant.parse("2026-06-16T10:05:00Z"),
                        Instant.parse("2026-06-16T10:01:00Z"),
                        null
                ))
                .toList();
        stubDashboardData(
                List.of(new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 99, 0, 1, 100, true)),
                new DemoOrderStatsResponse(10, 0, 10, 0, 0),
                new DemoPaymentStatsResponse(0, 10, 0),
                orders
        );

        DemoDashboardResponse response = service.dashboard();

        assertThat(response.recentOrders()).hasSize(10);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce())
                .query(sql.capture(), any(RowMapper.class), any());       
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement)
                        .contains("WHERE o.event_id = ?")
                        .contains("LIMIT 10")
        );
    }

    @Test
    void resetDeletesOnlyDemoPaymentsAndOrdersAndRestoresInventory() {
        stubDashboardData(
                List.of(new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 100, 0, 0, 100, true)),
                new DemoOrderStatsResponse(0, 0, 0, 0, 0),
                new DemoPaymentStatsResponse(0, 0, 0),
                List.of()
        );
        when(jdbcTemplate.update(anyString(), isA(Object.class))).thenReturn(5, 3);
        when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);

        DemoResetResponse response = service.reset();

        assertThat(response.deletedPayments()).isEqualTo(5);
        assertThat(response.deletedOrders()).isEqualTo(3);
        assertThat(response.availableStock()).isEqualTo(100);
        assertThat(response.inventoryConsistent()).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), isA(Object.class));
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement)
                        .contains("DELETE FROM payment_records pr")
                        .contains("USING ticket_orders o")
                        .contains("AND o.event_id = ?")
        );
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement).isEqualTo("DELETE FROM ticket_orders WHERE event_id = ?")
        );
    }

    @Test
    void resetRestoresEveryDemoTierWithoutTruncateOrSequenceReset() {
        stubDashboardData(
                List.of(new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 100, 0, 0, 100, true)),
                new DemoOrderStatsResponse(0, 0, 0, 0, 0),
                new DemoPaymentStatsResponse(0, 0, 0),
                List.of()
        );
        when(jdbcTemplate.update(anyString(), isA(Object.class))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);

        service.reset();

        List<String> updateStatements = org.mockito.Mockito
                .mockingDetails(jdbcTemplate)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .map(invocation -> (String) invocation.getArgument(0))
                .toList();

        assertThat(updateStatements).anySatisfy(statement ->
                assertThat(statement)
                        .contains("available_stock = tt.total_stock")
                        .contains("reserved_stock = 0")
                        .contains("sold_stock = 0")
                        .contains("version = ti.version + 1")
                        .doesNotContain("TRUNCATE")
                        .doesNotContain("ALTER SEQUENCE")
        );
}

    @Test
    void resetIsRepeatableWhenThereAreNoOrders() {
        stubDashboardData(
                List.of(new DemoTicketTierInventoryResponse(1L, "VIP", "VIP", 100, 100, 0, 0, 100, true)),
                new DemoOrderStatsResponse(0, 0, 0, 0, 0),
                new DemoPaymentStatsResponse(0, 0, 0),
                List.of()
        );
        when(jdbcTemplate.update(anyString(), isA(Object.class))).thenReturn(0, 0);
        when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);

        DemoResetResponse response = service.reset();

        assertThat(response.deletedOrders()).isZero();
        assertThat(response.deletedPayments()).isZero();
        assertThat(response.inventoryConsistent()).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubDashboardData(
            List<DemoTicketTierInventoryResponse> tiers,
            DemoOrderStatsResponse orderStats,
            DemoPaymentStatsResponse paymentStats,
            List<DemoOrderResponse> recentOrders
    ) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(DemoApplicationService.DEMO_EVENT_SLUG)))
                .thenReturn(new DemoEventResponse(1L, "ticketforge-opening-live", "TicketForge Opening Live", "ON_SALE"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(DemoApplicationService.DEMO_EVENT_SLUG)))
                .thenReturn((List) tiers);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(orderStats, paymentStats);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn((List) recentOrders);
    }
}
