package io.github.chikachi.ticketforge.loadtest.api;

import io.github.chikachi.ticketforge.loadtest.application.LoadTestDataService;
import io.github.chikachi.ticketforge.loadtest.application.LoadTestSecretVerifier;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("loadtest")
@RestController
@RequestMapping("/api/load-test")
public class LoadTestController {

    private final LoadTestDataService loadTestDataService;
    private final LoadTestSecretVerifier secretVerifier;

    public LoadTestController(LoadTestDataService loadTestDataService, LoadTestSecretVerifier secretVerifier) {
        this.loadTestDataService = loadTestDataService;
        this.secretVerifier = secretVerifier;
    }

    @GetMapping("/profile")
    public LoadTestProfileResponse profile(@RequestHeader("X-Load-Test-Secret") String secret) {
        secretVerifier.verify(secret);
        return new LoadTestProfileResponse(true, "loadtest");
    }

    @PostMapping("/reset")
    public LoadTestResetResponse reset(
            @RequestHeader("X-Load-Test-Secret") String secret,
            @Valid @RequestBody LoadTestResetRequest request
    ) {
        secretVerifier.verify(secret);
        return loadTestDataService.reset(request);
    }

    @GetMapping("/state")
    public LoadTestStateResponse state(
            @RequestHeader("X-Load-Test-Secret") String secret,
            @RequestParam String eventSlug
    ) {
        secretVerifier.verify(secret);
        return loadTestDataService.state(eventSlug);
    }
}
