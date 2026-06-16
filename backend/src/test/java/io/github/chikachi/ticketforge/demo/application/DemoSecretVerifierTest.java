package io.github.chikachi.ticketforge.demo.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class DemoSecretVerifierTest {

    @Test
    void wrongSecretReturnsUnauthorizedApiException() {
        DemoProperties properties = new DemoProperties();
        properties.setEnabled(true);
        properties.setSecret("expected");
        DemoSecretVerifier verifier = new DemoSecretVerifier(properties);

        assertThatThrownBy(() -> verifier.verify("wrong"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("DEMO_UNAUTHORIZED"));
    }

    @Test
    void disabledDemoRejectsEvenMatchingSecret() {
        DemoProperties properties = new DemoProperties();
        properties.setEnabled(false);
        properties.setSecret("expected");
        DemoSecretVerifier verifier = new DemoSecretVerifier(properties);

        assertThatThrownBy(() -> verifier.verify("expected"))
                .isInstanceOf(ApiException.class);
    }
}
