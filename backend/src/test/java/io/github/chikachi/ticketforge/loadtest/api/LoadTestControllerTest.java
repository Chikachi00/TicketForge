package io.github.chikachi.ticketforge.loadtest.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.loadtest.application.LoadTestDataService;
import io.github.chikachi.ticketforge.loadtest.application.LoadTestSecretVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("loadtest")
@WebMvcTest(LoadTestController.class)
class LoadTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoadTestDataService loadTestDataService;

    @MockBean
    private LoadTestSecretVerifier secretVerifier;

    @Test
    void controllerIsRestrictedToLoadtestProfile() {
        Profile profile = LoadTestController.class.getAnnotation(Profile.class);
        org.assertj.core.api.Assertions.assertThat(profile.value()).contains("loadtest");
    }

    @Test
    void profileEndpointIsAvailableWhenControllerIsLoaded() throws Exception {
        mockMvc.perform(get("/api/load-test/profile").header("X-Load-Test-Secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.profile").value("loadtest"));
    }

    @Test
    void wrongSecretReturns401() throws Exception {
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_LOAD_TEST_SECRET", "Invalid load-test secret"))
                .when(secretVerifier).verify("wrong");

        mockMvc.perform(get("/api/load-test/profile").header("X-Load-Test-Secret", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOAD_TEST_SECRET"));
    }

    @Test
    void resetReturnsFixtureMetadata() throws Exception {
        when(loadTestDataService.reset(any(LoadTestResetRequest.class)))
                .thenReturn(new LoadTestResetResponse("ticketforge-load-test-live", 10L, 20L, 100, 1000));

        mockMvc.perform(post("/api/load-test/reset")
                        .header("X-Load-Test-Secret", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventSlug":"ticketforge-load-test-live",
                                  "ticketCode":"LOAD",
                                  "totalStock":100,
                                  "userCount":1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketTierId").value(20))
                .andExpect(jsonPath("$.userCount").value(1000));
    }
}
