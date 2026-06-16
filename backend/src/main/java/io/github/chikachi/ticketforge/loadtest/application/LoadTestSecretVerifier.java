package io.github.chikachi.ticketforge.loadtest.application;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("loadtest")
@Component
public class LoadTestSecretVerifier {

    private final LoadTestProperties properties;

    public LoadTestSecretVerifier(LoadTestProperties properties) {
        this.properties = properties;
    }

    public void verify(String providedSecret) {
        byte[] expected = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSecret == null ? new byte[0] : providedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_LOAD_TEST_SECRET", "Invalid load-test secret");
        }
    }
}
