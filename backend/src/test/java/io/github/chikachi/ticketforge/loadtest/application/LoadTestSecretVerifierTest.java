package io.github.chikachi.ticketforge.loadtest.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class LoadTestSecretVerifierTest {

    @Test
    void wrongSecretReturnsUnauthorizedApiException() {
        LoadTestProperties properties = new LoadTestProperties();
        properties.setSecret("ticketforge-local-loadtest-secret");
        LoadTestSecretVerifier verifier = new LoadTestSecretVerifier(properties);

        assertThatThrownBy(() -> verifier.verify("wrong"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid load-test secret");
    }
}
