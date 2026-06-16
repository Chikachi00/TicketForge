package io.github.chikachi.ticketforge.loadtest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.chikachi.ticketforge.loadtest.api.LoadTestResetRequest;
import io.github.chikachi.ticketforge.loadtest.api.LoadTestStateResponse;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LoadTestDataServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private LoadTestDataService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LoadTestDataService(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void resetOnlyTargetsDedicatedLoadTestDataAndCreatesRequestedUsers() {
        when(jdbcTemplate.query(eq("SELECT id FROM events WHERE slug = ?"), any(org.springframework.jdbc.core.ResultSetExtractor.class), any()))
                .thenReturn(10L);
        when(jdbcTemplate.query(eq("SELECT id FROM ticket_tiers WHERE event_id = ? AND code = ?"), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(), any()))
                .thenReturn(20L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.reset(new LoadTestResetRequest("ticketforge-load-test-live", "LOAD", 100, 1000));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement).contains("DELETE FROM app_users").contains("loadtest-user-%@ticketforge.local"));
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement).contains("available_stock = EXCLUDED.available_stock").contains("reserved_stock = 0").contains("sold_stock = 0"));
        assertThat(sql.getAllValues()).anySatisfy(statement ->
                assertThat(statement).contains("generate_series(1, ?)"));
    }

    @Test
    void stateCalculatesInventoryInvariantAndOversell() throws Exception {
        when(resultSet.getString("event_slug")).thenReturn("ticketforge-load-test-live");
        when(resultSet.getLong("ticket_tier_id")).thenReturn(20L);
        when(resultSet.getInt("total_stock")).thenReturn(100);
        when(resultSet.getInt("available_stock")).thenReturn(0);
        when(resultSet.getInt("reserved_stock")).thenReturn(101);
        when(resultSet.getInt("sold_stock")).thenReturn(0);
        when(resultSet.getInt("calculated_total")).thenReturn(101);
        when(resultSet.getLong("pending_orders")).thenReturn(101L);
        when(resultSet.getLong("paid_orders")).thenReturn(0L);
        when(resultSet.getLong("cancelled_orders")).thenReturn(0L);
        when(resultSet.getLong("payment_success_count")).thenReturn(0L);
        when(resultSet.getLong("payment_failed_count")).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> invocation.<RowMapper<LoadTestStateResponse>>getArgument(1).mapRow(resultSet, 0));

        LoadTestStateResponse response = service.state("ticketforge-load-test-live");

        assertThat(response.inventoryConsistent()).isFalse();
        assertThat(response.oversellDetected()).isTrue();
        assertThat(response.negativeInventoryDetected()).isFalse();
    }
}
