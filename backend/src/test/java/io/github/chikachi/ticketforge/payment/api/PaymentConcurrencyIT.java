package io.github.chikachi.ticketforge.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chikachi.ticketforge.order.api.OrderResponse;
import io.github.chikachi.ticketforge.order.application.OrderApplicationService;
import io.github.chikachi.ticketforge.payment.application.PaymentSignatureService;
import java.math.BigDecimal;
import java.time.Instant;
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
class PaymentConcurrencyIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentSignatureService paymentSignatureService;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Test
    void duplicateSuccessCallbacksTransferReservedStockOnce() throws Exception {
        Flow flow = createReservedPaymentFlow();
        PaymentCallbackRequest request = successRequest(flow, "EVT-" + UUID.randomUUID());
        String signature = paymentSignatureService.sign(request);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ResponseEntity<PaymentCallbackResponse>>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> sendCallback(request, signature));
            }

            var futures = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
            List<ResponseEntity<PaymentCallbackResponse>> responses = futures.stream()
                    .filter(future -> !future.isCancelled())
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(responses).hasSize(12);
            assertThat(responses).allMatch(response -> response.getStatusCode().is2xxSuccessful());
            assertThat(responses.stream().filter(response -> Boolean.TRUE.equals(response.getBody().idempotentReplay())).count()).isGreaterThan(0);

            OrderInventorySnapshot snapshot = snapshot(flow.orderNumber(), flow.ticketTierId());
            assertThat(snapshot.orderStatus()).isEqualTo("PAID");
            assertThat(snapshot.paymentSuccessCount()).isEqualTo(1);
            assertThat(snapshot.availableStock()).isZero();
            assertThat(snapshot.reservedStock()).isZero();
            assertThat(snapshot.soldStock()).isEqualTo(1);
            assertThat(snapshot.calculatedTotal()).isEqualTo(snapshot.totalStock());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void paymentAndActiveCancelRaceHasOneFinalInventoryTransition() throws Exception {
        Flow flow = createReservedPaymentFlow();
        PaymentCallbackRequest request = successRequest(flow, "EVT-" + UUID.randomUUID());
        String signature = paymentSignatureService.sign(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> tasks = List.of(
                    () -> sendCallback(request, signature),
                    () -> cancelOrder(flow.email(), flow.orderNumber())
            );
            executor.invokeAll(tasks, 20, TimeUnit.SECONDS);

            OrderInventorySnapshot snapshot = snapshot(flow.orderNumber(), flow.ticketTierId());
            assertFinalInventory(snapshot);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void paymentAndExpirationRaceHasOneFinalInventoryTransition() throws Exception {
        Flow flow = createReservedPaymentFlow();
        jdbcTemplate.update(
            "UPDATE ticket_orders SET expires_at = created_at WHERE order_number = ?",
            flow.orderNumber()
        );
        PaymentCallbackRequest request = successRequest(flow, "EVT-" + UUID.randomUUID());
        String signature = paymentSignatureService.sign(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> tasks = List.of(
                    () -> sendCallback(request, signature),
                    () -> {
                        orderApplicationService.expireOrder(flow.orderNumber());
                        return null;
                    }
            );
            executor.invokeAll(tasks, 20, TimeUnit.SECONDS);

            OrderInventorySnapshot snapshot = snapshot(flow.orderNumber(), flow.ticketTierId());
            assertFinalInventory(snapshot);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertFinalInventory(OrderInventorySnapshot snapshot) {
        assertThat(snapshot.orderStatus()).isIn("PAID", "CANCELLED");
        assertThat(snapshot.reservedStock()).isZero();
        assertThat(snapshot.calculatedTotal()).isEqualTo(snapshot.totalStock());
        if (snapshot.orderStatus().equals("PAID")) {
            assertThat(snapshot.availableStock()).isZero();
            assertThat(snapshot.soldStock()).isEqualTo(1);
        } else {
            assertThat(snapshot.availableStock()).isEqualTo(1);
            assertThat(snapshot.soldStock()).isZero();
        }
    }

    private Flow createReservedPaymentFlow() {
        Fixture fixture = createFixture(1);
        String email = createUser("pay-" + UUID.randomUUID() + "@ticketforge.local");
        OrderResponse order = createOrder(email, UUID.randomUUID().toString(), fixture.ticketTierId(), 1).getBody();
        PaymentSessionResponse payment = createPaymentSession(email, UUID.randomUUID().toString(), order.orderNumber()).getBody();
        return new Flow(email, order.orderNumber(), payment.paymentTransactionId(), payment.amount(), fixture.ticketTierId());
    }

    private PaymentCallbackRequest successRequest(Flow flow, String providerEventId) {
        return new PaymentCallbackRequest(
                providerEventId,
                flow.paymentTransactionId(),
                flow.orderNumber(),
                "SUCCESS",
                flow.amount(),
                "CNY",
                Instant.parse("2026-06-16T10:02:00Z"),
                null
        );
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

    private ResponseEntity<PaymentSessionResponse> createPaymentSession(String email, String idempotencyKey, String orderNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Email", email);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(
                "/api/payments/orders/" + orderNumber,
                HttpMethod.POST,
                new HttpEntity<Void>(headers),
                PaymentSessionResponse.class
        );
    }

    private ResponseEntity<PaymentCallbackResponse> sendCallback(PaymentCallbackRequest request, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Payment-Signature", signature);
        return restTemplate.exchange(
                "/api/payments/callback",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                PaymentCallbackResponse.class
        );
    }

    private ResponseEntity<OrderResponse> cancelOrder(String email, String orderNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Email", email);
        return restTemplate.exchange(
                "/api/orders/" + orderNumber + "/cancel",
                HttpMethod.POST,
                new HttpEntity<Void>(headers),
                OrderResponse.class
        );
    }

    private String createUser(String email) {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (email, display_name, role)
                        VALUES (?, 'Payment IT User', 'USER')
                        """,
                email
        );
        return email;
    }

    private Fixture createFixture(int stock) {
        String slug = "payment-it-event-" + UUID.randomUUID();
        Long eventId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO events (slug, name, venue, description, performance_at, sales_start_at, status)
                        VALUES (?, 'Payment Integration Live', 'Test Venue', 'Integration fixture',
                                NOW() + INTERVAL '30 days', NOW() - INTERVAL '1 day', 'ON_SALE')
                        RETURNING id
                        """,
                Long.class,
                slug
        );
        Long tierId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO ticket_tiers (event_id, code, name, price, total_stock)
                        VALUES (?, 'PAY', 'Payment Tier', 1280.00, ?)
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

    private OrderInventorySnapshot snapshot(String orderNumber, Long ticketTierId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT o.status AS order_status,
                               tt.total_stock,
                               ti.available_stock,
                               ti.reserved_stock,
                               ti.sold_stock,
                               ti.available_stock + ti.reserved_stock + ti.sold_stock AS calculated_total,
                               (SELECT COUNT(*) FROM payment_records pr WHERE pr.order_id = o.id AND pr.status = 'SUCCESS') AS payment_success_count
                        FROM ticket_orders o
                        JOIN ticket_tiers tt ON tt.id = o.ticket_tier_id
                        JOIN ticket_inventory ti ON ti.ticket_tier_id = tt.id
                        WHERE o.order_number = ?
                          AND tt.id = ?
                        """,
                (rs, rowNum) -> new OrderInventorySnapshot(
                        rs.getString("order_status"),
                        rs.getInt("total_stock"),
                        rs.getInt("available_stock"),
                        rs.getInt("reserved_stock"),
                        rs.getInt("sold_stock"),
                        rs.getInt("calculated_total"),
                        rs.getInt("payment_success_count")
                ),
                orderNumber,
                ticketTierId
        );
    }

    private record Fixture(Long eventId, Long ticketTierId) {
    }

    private record Flow(String email, String orderNumber, String paymentTransactionId, BigDecimal amount, Long ticketTierId) {
    }

    private record OrderInventorySnapshot(
            String orderStatus,
            int totalStock,
            int availableStock,
            int reservedStock,
            int soldStock,
            int calculatedTotal,
            int paymentSuccessCount
    ) {
    }
}
