package io.github.chikachi.ticketforge.demo.application;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("demo & !prod")
public class DemoSecretVerifier {

    private final DemoProperties properties;

    public DemoSecretVerifier(DemoProperties properties) {
        this.properties = properties;
    }

    public void verify(String secret) {
        if (!properties.isEnabled() || secret == null || !secret.equals(properties.getSecret())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DEMO_UNAUTHORIZED", "Invalid demo secret");
        }
    }
}
