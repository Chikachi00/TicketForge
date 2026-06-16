package io.github.chikachi.ticketforge.demo.application;

import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.demo.api.DemoDashboardResponse;
import io.github.chikachi.ticketforge.demo.api.DemoEventResponse;
import io.github.chikachi.ticketforge.demo.api.DemoInventoryResponse;
import io.github.chikachi.ticketforge.demo.api.DemoOrderResponse;
import io.github.chikachi.ticketforge.demo.api.DemoOrderStatsResponse;
import io.github.chikachi.ticketforge.demo.api.DemoPaymentStatsResponse;
import io.github.chikachi.ticketforge.demo.api.DemoResetResponse;
import io.github.chikachi.ticketforge.demo.api.DemoTicketTierInventoryResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo & !prod")
public class DemoApplicationService {

    public static final String DEMO_EVENT_SLUG = "ticketforge-opening-live";

    private final JdbcTemplate jdbcTemplate;

    private final Clock clock;

    public DemoApplicationService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DemoDashboardResponse dashboard() {
        DemoEventResponse event = findDemoEvent();
        List<DemoTicketTierInventoryResponse> tiers = ticketTiers(event.slug());
        DemoInventoryResponse inventory = inventory(tiers);
        return new DemoDashboardResponse(
                event,
                inventory,
                tiers,
                orderStats(event.id()),
                paymentStats(event.id()),
                recentOrders(event.id()),
                Instant.now(clock)
        );
    }

    @Transactional
    public DemoResetResponse reset() {
        DemoEventResponse event = findDemoEvent();
        Instant now = Instant.now(clock);

        int deletedPayments = jdbcTemplate.update(
                """
                        DELETE FROM payment_records pr
                        USING ticket_orders o
                        WHERE pr.order_id = o.id
                          AND o.event_id = ?
                        """,
                event.id()
        );
        int deletedOrders = jdbcTemplate.update(
                "DELETE FROM ticket_orders WHERE event_id = ?",
                event.id()
        );

        jdbcTemplate.update(
                """
                        UPDATE ticket_inventory ti
                        SET available_stock = tt.total_stock,
                            reserved_stock = 0,
                            sold_stock = 0,
                            version = ti.version + 1,
                            updated_at = ?
                        FROM ticket_tiers tt
                        WHERE ti.ticket_tier_id = tt.id
                          AND tt.event_id = ?
                        """,
                now,
                event.id()
        );
        jdbcTemplate.update(
                """
                        UPDATE events
                        SET status = 'ON_SALE',
                            sales_start_at = LEAST(sales_start_at, NOW() - INTERVAL '1 minute'),
                            updated_at = ?
                        WHERE id = ?
                        """,
                now,
                event.id()
        );

        DemoInventoryResponse inventory = inventory(ticketTiers(event.slug()));
        return new DemoResetResponse(
                event.slug(),
                deletedOrders,
                deletedPayments,
                inventory.availableStock(),
                inventory.reservedStock(),
                inventory.soldStock(),
                inventory.inventoryConsistent(),
                now
        );
    }

    private DemoEventResponse findDemoEvent() {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            SELECT id, slug, name, status
                            FROM events
                            WHERE slug = ?
                            """,
                    (rs, rowNum) -> new DemoEventResponse(
                            rs.getLong("id"),
                            rs.getString("slug"),
                            rs.getString("name"),
                            rs.getString("status")
                    ),
                    DEMO_EVENT_SLUG
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("DEMO_EVENT_NOT_FOUND", "Demo event was not found");
        }
    }

    private List<DemoTicketTierInventoryResponse> ticketTiers(String eventSlug) {
        return jdbcTemplate.query(
                """
                        SELECT tt.id,
                               tt.code,
                               tt.name,
                               tt.total_stock,
                               ti.available_stock,
                               ti.reserved_stock,
                               ti.sold_stock
                        FROM events e
                        JOIN ticket_tiers tt ON tt.event_id = e.id
                        JOIN ticket_inventory ti ON ti.ticket_tier_id = tt.id
                        WHERE e.slug = ?
                        ORDER BY tt.price DESC, tt.id ASC
                        """,
                (rs, rowNum) -> mapTier(rs),
                eventSlug
        );
    }

    private DemoTicketTierInventoryResponse mapTier(ResultSet rs) throws SQLException {
        int totalStock = rs.getInt("total_stock");
        int availableStock = rs.getInt("available_stock");
        int reservedStock = rs.getInt("reserved_stock");
        int soldStock = rs.getInt("sold_stock");
        int calculatedTotal = availableStock + reservedStock + soldStock;
        return new DemoTicketTierInventoryResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                totalStock,
                availableStock,
                reservedStock,
                soldStock,
                calculatedTotal,
                calculatedTotal == totalStock
        );
    }

    private DemoInventoryResponse inventory(List<DemoTicketTierInventoryResponse> tiers) {
        int totalStock = tiers.stream().mapToInt(DemoTicketTierInventoryResponse::totalStock).sum();
        int availableStock = tiers.stream().mapToInt(DemoTicketTierInventoryResponse::availableStock).sum();
        int reservedStock = tiers.stream().mapToInt(DemoTicketTierInventoryResponse::reservedStock).sum();
        int soldStock = tiers.stream().mapToInt(DemoTicketTierInventoryResponse::soldStock).sum();
        int calculatedTotal = availableStock + reservedStock + soldStock;
        boolean tiersConsistent = tiers.stream().allMatch(DemoTicketTierInventoryResponse::inventoryConsistent);
        return new DemoInventoryResponse(
                totalStock,
                availableStock,
                reservedStock,
                soldStock,
                calculatedTotal,
                calculatedTotal == totalStock && tiersConsistent
        );
    }

    private DemoOrderStatsResponse orderStats(Long eventId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT') AS pending_payment,
                               COUNT(*) FILTER (WHERE status = 'PAID') AS paid,
                               COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
                               COUNT(*) FILTER (WHERE status = 'REFUNDED') AS refunded
                        FROM ticket_orders
                        WHERE event_id = ?
                        """,
                (rs, rowNum) -> new DemoOrderStatsResponse(
                        rs.getLong("total"),
                        rs.getLong("pending_payment"),
                        rs.getLong("paid"),
                        rs.getLong("cancelled"),
                        rs.getLong("refunded")
                ),
                eventId
        );
    }

    private DemoPaymentStatsResponse paymentStats(Long eventId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FILTER (WHERE pr.status = 'PENDING') AS pending,
                               COUNT(*) FILTER (WHERE pr.status = 'SUCCESS') AS success,
                               COUNT(*) FILTER (WHERE pr.status = 'FAILED') AS failed
                        FROM payment_records pr
                        JOIN ticket_orders o ON o.id = pr.order_id
                        WHERE o.event_id = ?
                        """,
                (rs, rowNum) -> new DemoPaymentStatsResponse(
                        rs.getLong("pending"),
                        rs.getLong("success"),
                        rs.getLong("failed")
                ),
                eventId
        );
    }

    private List<DemoOrderResponse> recentOrders(Long eventId) {
        return jdbcTemplate.query(
                """
                        SELECT o.order_number,
                               tt.code AS ticket_tier_code,
                               tt.name AS ticket_tier_name,
                               o.quantity,
                               o.status,
                               o.total_amount,
                               o.created_at,
                               o.expires_at,
                               o.paid_at,
                               o.cancelled_at,
                               lp.payment_transaction_id,
                               lp.status AS payment_status
                        FROM ticket_orders o
                        JOIN ticket_tiers tt ON tt.id = o.ticket_tier_id
                        LEFT JOIN LATERAL (
                            SELECT pr.payment_transaction_id, pr.status
                            FROM payment_records pr
                            WHERE pr.order_id = o.id
                            ORDER BY pr.created_at DESC, pr.id DESC
                            LIMIT 1
                        ) lp ON TRUE
                        WHERE o.event_id = ?
                        ORDER BY o.created_at DESC, o.id DESC
                        LIMIT 10
                        """,
                (rs, rowNum) -> new DemoOrderResponse(
                        rs.getString("order_number"),
                        rs.getString("ticket_tier_code"),
                        rs.getString("ticket_tier_name"),
                        rs.getInt("quantity"),
                        rs.getString("status"),
                        rs.getObject("total_amount", BigDecimal.class),
                        rs.getString("payment_transaction_id"),
                        rs.getString("payment_status"),
                        rs.getObject("created_at", Instant.class),
                        rs.getObject("expires_at", Instant.class),
                        rs.getObject("paid_at", Instant.class),
                        rs.getObject("cancelled_at", Instant.class)
                ),
                eventId
        );
    }
}
