package io.github.chikachi.ticketforge.loadtest.application;

import io.github.chikachi.ticketforge.loadtest.api.LoadTestResetRequest;
import io.github.chikachi.ticketforge.loadtest.api.LoadTestResetResponse;
import io.github.chikachi.ticketforge.loadtest.api.LoadTestStateResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("loadtest")
@Service
public class LoadTestDataService {

    private static final String LOAD_TEST_USER_PREFIX = "loadtest-user-";
    private static final BigDecimal LOAD_TEST_PRICE = new BigDecimal("99.00");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public LoadTestDataService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public LoadTestResetResponse reset(LoadTestResetRequest request) {
        Instant now = Instant.now(clock);
        Long eventId = findEventId(request.eventSlug());
        if (eventId != null) {
            jdbcTemplate.update("""
                    DELETE FROM payment_records
                    WHERE order_id IN (SELECT id FROM ticket_orders WHERE event_id = ?)
                    """, eventId);
            jdbcTemplate.update("DELETE FROM ticket_orders WHERE event_id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM app_users WHERE email LIKE 'loadtest-user-%@ticketforge.local'");

        if (eventId == null) {
            eventId = jdbcTemplate.queryForObject("""
                    INSERT INTO events (slug, name, venue, description, performance_at, sales_start_at, status, created_at, updated_at)
                    VALUES (?, 'TicketForge Load Test Live', 'Load Test Venue', 'Dedicated load-test event',
                            ?, ?, 'ON_SALE', ?, ?)
                    RETURNING id
                    """, Long.class, request.eventSlug(), Timestamp.from(now.plusSeconds(86_400)),
                    Timestamp.from(now.minusSeconds(60)), Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbcTemplate.update("""
                    UPDATE events
                    SET name = 'TicketForge Load Test Live',
                        venue = 'Load Test Venue',
                        description = 'Dedicated load-test event',
                        performance_at = ?,
                        sales_start_at = ?,
                        status = 'ON_SALE',
                        updated_at = ?
                    WHERE id = ?
                    """, Timestamp.from(now.plusSeconds(86_400)), Timestamp.from(now.minusSeconds(60)),
                    Timestamp.from(now), eventId);
        }

        Long ticketTierId = findTicketTierId(eventId, request.ticketCode());
        if (ticketTierId == null) {
            ticketTierId = jdbcTemplate.queryForObject("""
                    INSERT INTO ticket_tiers (event_id, code, name, price, total_stock, created_at, updated_at)
                    VALUES (?, ?, 'Load Test Tier', ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, eventId, request.ticketCode(), LOAD_TEST_PRICE, request.totalStock(),
                    Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbcTemplate.update("""
                    UPDATE ticket_tiers
                    SET name = 'Load Test Tier',
                        price = ?,
                        total_stock = ?,
                        updated_at = ?
                    WHERE id = ?
                    """, LOAD_TEST_PRICE, request.totalStock(), Timestamp.from(now), ticketTierId);
        }

        jdbcTemplate.update("""
                INSERT INTO ticket_inventory (ticket_tier_id, available_stock, reserved_stock, sold_stock, version, updated_at)
                VALUES (?, ?, 0, 0, 0, ?)
                ON CONFLICT (ticket_tier_id)
                DO UPDATE SET available_stock = EXCLUDED.available_stock,
                              reserved_stock = 0,
                              sold_stock = 0,
                              version = ticket_inventory.version + 1,
                              updated_at = EXCLUDED.updated_at
                """, ticketTierId, request.totalStock(), Timestamp.from(now));

        jdbcTemplate.update("""
                INSERT INTO app_users (email, display_name, role, created_at, updated_at)
                SELECT 'loadtest-user-' || lpad(series::text, 6, '0') || '@ticketforge.local',
                       'Load Test User ' || lpad(series::text, 6, '0'),
                       'USER',
                       ?,
                       ?
                FROM generate_series(1, ?) AS series
                ON CONFLICT (email)
                DO UPDATE SET display_name = EXCLUDED.display_name,
                              updated_at = EXCLUDED.updated_at
                """, Timestamp.from(now), Timestamp.from(now), request.userCount());

        return new LoadTestResetResponse(request.eventSlug(), eventId, ticketTierId, request.totalStock(), request.userCount());
    }

    @Transactional(readOnly = true)
    public LoadTestStateResponse state(String eventSlug) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
                SELECT e.slug AS event_slug,
                       tt.id AS ticket_tier_id,
                       tt.total_stock,
                       ti.available_stock,
                       ti.reserved_stock,
                       ti.sold_stock,
                       ti.available_stock + ti.reserved_stock + ti.sold_stock AS calculated_total,
                       COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'PENDING_PAYMENT') AS pending_orders,
                       COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'PAID') AS paid_orders,
                       COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'CANCELLED') AS cancelled_orders,
                       COUNT(p.id) FILTER (WHERE p.status = 'SUCCESS') AS payment_success_count,
                       COUNT(p.id) FILTER (WHERE p.status = 'FAILED') AS payment_failed_count
                FROM events e
                JOIN ticket_tiers tt ON tt.event_id = e.id
                JOIN ticket_inventory ti ON ti.ticket_tier_id = tt.id
                LEFT JOIN ticket_orders o ON o.ticket_tier_id = tt.id
                LEFT JOIN payment_records p ON p.order_id = o.id
                WHERE e.slug = ?
                GROUP BY e.slug, tt.id, tt.total_stock, ti.available_stock, ti.reserved_stock, ti.sold_stock
                ORDER BY tt.id
                LIMIT 1
                """, (rs, rowNum) -> {
            int totalStock = rs.getInt("total_stock");
            int available = rs.getInt("available_stock");
            int reserved = rs.getInt("reserved_stock");
            int sold = rs.getInt("sold_stock");
            int calculatedTotal = rs.getInt("calculated_total");
            long pendingOrders = rs.getLong("pending_orders");
            long paidOrders = rs.getLong("paid_orders");
            boolean negative = available < 0 || reserved < 0 || sold < 0;
            boolean consistent = calculatedTotal == totalStock;
            boolean oversell = reserved + sold > totalStock || pendingOrders + paidOrders > totalStock;
            return new LoadTestStateResponse(
                    rs.getString("event_slug"),
                    rs.getLong("ticket_tier_id"),
                    totalStock,
                    available,
                    reserved,
                    sold,
                    pendingOrders,
                    paidOrders,
                    rs.getLong("cancelled_orders"),
                    rs.getLong("payment_success_count"),
                    rs.getLong("payment_failed_count"),
                    calculatedTotal,
                    consistent,
                    negative,
                    oversell
            );
        }, eventSlug));
    }

    private Long findEventId(String eventSlug) {
        return jdbcTemplate.query("SELECT id FROM events WHERE slug = ?",
                rs -> rs.next() ? rs.getLong("id") : null, eventSlug);
    }

    private Long findTicketTierId(Long eventId, String ticketCode) {
        return jdbcTemplate.query("SELECT id FROM ticket_tiers WHERE event_id = ? AND code = ?",
                rs -> rs.next() ? rs.getLong("id") : null, eventId, ticketCode);
    }
}
