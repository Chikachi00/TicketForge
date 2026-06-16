package io.github.chikachi.ticketforge.demo.api;

import io.github.chikachi.ticketforge.demo.application.DemoApplicationService;
import io.github.chikachi.ticketforge.demo.application.DemoSecretVerifier;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("demo & !prod")
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoApplicationService demoApplicationService;

    private final DemoSecretVerifier secretVerifier;

    public DemoController(DemoApplicationService demoApplicationService, DemoSecretVerifier secretVerifier) {
        this.demoApplicationService = demoApplicationService;
        this.secretVerifier = secretVerifier;
    }

    @GetMapping("/profile")
    public DemoProfileResponse profile(@RequestHeader("X-Demo-Secret") String secret) {
        secretVerifier.verify(secret);
        return new DemoProfileResponse(true, "demo", DemoApplicationService.DEMO_EVENT_SLUG);
    }

    @GetMapping("/dashboard")
    public DemoDashboardResponse dashboard(@RequestHeader("X-Demo-Secret") String secret) {
        secretVerifier.verify(secret);
        return demoApplicationService.dashboard();
    }

    @PostMapping("/reset")
    public DemoResetResponse reset(@RequestHeader("X-Demo-Secret") String secret) {
        secretVerifier.verify(secret);
        return demoApplicationService.reset();
    }
}
