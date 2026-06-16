package io.github.chikachi.ticketforge.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=${TF_IT_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ticketforge_test}",
        "spring.datasource.username=${TF_IT_DATASOURCE_USERNAME:ticketforge}",
        "spring.datasource.password=${TF_IT_DATASOURCE_PASSWORD:ticketforge_dev}",
        "ticketforge.orders.expiration-scan-delay-ms=600000"
})
class OrderConcurrencyIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentOrdersCannotOversellInventory() throws Exception {
        Fixture fixture = createFixture(10);
        List<String> emails = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            emails.add(createUser("buyer-" + index + "-" + UUID.randomUUID() + "@ticketforge.local"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<ResponseEntity<OrderResponse>>> tasks = emails.stream()
                    .map(email -> (Callable<ResponseEntity<OrderResponse>>) () -> createOrder(email, UUID.randomUUID().toString(), fixture.ticketTierId(), 1))
                    .toList();

            var futures = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
            long created = futures.stream()
                    .filter(future -> !future.isCancelled())
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                    .count();

            assertThat(created).isEqualTo(10);
            InventorySnapshot inventory = inventory(fixture.ticketTierId());
            assertThat(inventory.availableStock()).isZero();
            assertThat(inventory.reservedStock()).isEqualTo(10);
            assertThat(inventory.soldStock()).isZero();
            assertThat(inventory.calculatedTotal()).isEqualTo(inventory.totalStock());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentIdempotentReplayCreatesOnlyOneOrderAndReservesOnce() throws Exception {
        Fixture fixture = createFixture(10);
        String email = createUser("same-user-" + UUID.randomUUID() + "@ticketforge.local");
        String idempotencyKey = UUID.randomUUID().toString();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ResponseEntity<OrderResponse>>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> createOrder(email, idempotencyKey, fixture.ticketTierId(), 1));
            }

            var futures = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
            List<OrderResponse> successfulResponses = futures.stream()
                    .filter(future -> !future.isCancelled())
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .filter(response -> response.getStatusCode().is2xxSuccessful())
                    .map(ResponseEntity::getBody)
                    .toList();

            assertThat(successfulResponses).hasSize(12);
            assertThat(successfulResponses).extracting(OrderResponse::orderNumber).containsOnly(successfulResponses.getFirst().orderNumber());
            assertThat(successfulResponses).filteredOn(OrderResponse::idempotentReplay).hasSize(11);

            Long orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ticket_orders WHERE user_id = (SELECT id FROM app_users WHERE email = ?) AND idempotency_key = ?",
                    Long.class,
                    email,
                    idempotencyKey
            );
            assertThat(orderCount).isEqualTo(1L);

            InventorySnapshot inventory = inventory(fixture.ticketTierId());
            assertThat(inventory.availableStock()).isEqualTo(9);
            assertThat(inventory.reservedStock()).isEqualTo(1);
            assertThat(inventory.calculatedTotal()).isEqualTo(inventory.totalStock());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResponseEntity<OrderResponse> createOrder(String email, String idempotencyKey, Long ticketTierId, int quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Email", email);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(
                "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("ticketTierId", ticketTierId, "quantity", quantity), headers),
                OrderResponse.class
        );
    }

    private String createUser(String email) {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (email, display_name, role)
                        VALUES (?, 'Integration User', 'USER')
                        """,
                email
        );
        return email;
    }

    private Fixture createFixture(int stock) {
        String slug = "it-event-" + UUID.randomUUID();
        Long eventId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO events (slug, name, venue, description, performance_at, sales_start_at, status)
                        VALUES (?, 'Integration Live', 'Test Venue', 'Integration fixture',
                                NOW() + INTERVAL '30 days', NOW() - INTERVAL '1 day', 'ON_SALE')
                        RETURNING id
                        """,
                Long.class,
                slug
        );
        Long tierId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO ticket_tiers (event_id, code, name, price, total_stock)
                        VALUES (?, 'IT', 'Integration Tier', 99.00, ?)
                        RETURNING id
                        """,
                Long.class,
                eventId,
                stock
        );
        jdbcTemplate.update(
                """
                        INSERT INTO ticket_inventory (ticket_tier_id, available_stock, reserved_stock, sold_stock, version)
                        VALUES (?, ?, 0, 0, 0)
                        """,
                tierId,
                stock
        );
        return new Fixture(eventId, tierId);
    }

    private InventorySnapshot inventory(Long ticketTierId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT tt.total_stock,
                               ti.available_stock,
                               ti.reserved_stock,
                               ti.sold_stock,
                               ti.available_stock + ti.reserved_stock + ti.sold_stock AS calculated_total
                        FROM ticket_tiers tt
                        JOIN ticket_inventory ti ON ti.ticket_tier_id = tt.id
                        WHERE tt.id = ?
                        """,
                (rs, rowNum) -> new InventorySnapshot(
                        rs.getInt("total_stock"),
                        rs.getInt("available_stock"),
                        rs.getInt("reserved_stock"),
                        rs.getInt("sold_stock"),
                        rs.getInt("calculated_total")
                ),
                ticketTierId
        );
    }

    private record Fixture(Long eventId, Long ticketTierId) {
    }

    private record InventorySnapshot(
            int totalStock,
            int availableStock,
            int reservedStock,
            int soldStock,
            int calculatedTotal
    ) {
    }
}
