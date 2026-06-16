package io.github.chikachi.ticketforge.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderExpirationService orderExpirationService;
    private final OrderApplicationService orderApplicationService;

    public OrderExpirationScheduler(OrderExpirationService orderExpirationService,
                                    OrderApplicationService orderApplicationService) {
        this.orderExpirationService = orderExpirationService;
        this.orderApplicationService = orderApplicationService;
    }

    @Scheduled(fixedDelayString = "${ticketforge.orders.expiration-scan-delay-ms}")
    public void cancelExpiredOrders() {
        for (String orderNumber : orderExpirationService.findExpiredPendingOrderNumbers()) {
            try {
                orderApplicationService.expireOrder(orderNumber);
            } catch (RuntimeException exception) {
                log.error("Failed to expire order {}", orderNumber, exception);
            }
        }
    }
}
