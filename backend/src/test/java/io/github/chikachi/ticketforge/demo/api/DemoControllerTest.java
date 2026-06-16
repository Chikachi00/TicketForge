package io.github.chikachi.ticketforge.demo.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.demo.application.DemoApplicationService;
import io.github.chikachi.ticketforge.demo.application.DemoSecretVerifier;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("demo")
@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoApplicationService demoApplicationService;

    @MockBean
    private DemoSecretVerifier secretVerifier;

    @Test
    void controllerIsRestrictedToDemoProfileAndNotProd() {
        Profile profile = DemoController.class.getAnnotation(Profile.class);
        org.assertj.core.api.Assertions.assertThat(profile.value()).contains("demo & !prod");
    }

    @Test
    void profileEndpointIsAvailableInDemoProfile() throws Exception {
        mockMvc.perform(get("/api/demo/profile").header("X-Demo-Secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.profile").value("demo"))
                .andExpect(jsonPath("$.eventSlug").value("ticketforge-opening-live"));
    }

    @Test
    void wrongSecretReturns401() throws Exception {
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "DEMO_UNAUTHORIZED", "Invalid demo secret"))
                .when(secretVerifier).verify("wrong");

        mockMvc.perform(get("/api/demo/dashboard").header("X-Demo-Secret", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEMO_UNAUTHORIZED"));
    }

    @Test
    void dashboardReturnsDemoAggregate() throws Exception {
        when(demoApplicationService.dashboard()).thenReturn(new DemoDashboardResponse(
                new DemoEventResponse(1L, "ticketforge-opening-live", "TicketForge Opening Live", "ON_SALE"),
                new DemoInventoryResponse(600, 598, 1, 1, 600, true),
                List.of(new DemoTicketTierInventoryResponse(10L, "VIP", "VIP", 100, 99, 0, 1, 100, true)),
                new DemoOrderStatsResponse(2, 0, 1, 1, 0),
                new DemoPaymentStatsResponse(0, 1, 1),
                List.of(),
                Instant.parse("2026-06-16T10:00:00Z")
        ));

        mockMvc.perform(get("/api/demo/dashboard").header("X-Demo-Secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.slug").value("ticketforge-opening-live"))
                .andExpect(jsonPath("$.inventory.inventoryConsistent").value(true))
                .andExpect(jsonPath("$.orders.paid").value(1))
                .andExpect(jsonPath("$.payments.failed").value(1));
    }

    @Test
    void resetReturnsScopedResetResult() throws Exception {
        when(demoApplicationService.reset()).thenReturn(new DemoResetResponse(
                "ticketforge-opening-live",
                5,
                3,
                1600,
                0,
                0,
                true,
                Instant.parse("2026-06-16T10:00:00Z")
        ));

        mockMvc.perform(post("/api/demo/reset").header("X-Demo-Secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventSlug").value("ticketforge-opening-live"))
                .andExpect(jsonPath("$.deletedOrders").value(5))
                .andExpect(jsonPath("$.deletedPayments").value(3))
                .andExpect(jsonPath("$.inventoryConsistent").value(true));
    }
}
