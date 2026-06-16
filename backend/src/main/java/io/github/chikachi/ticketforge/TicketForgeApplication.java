package io.github.chikachi.ticketforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TicketForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketForgeApplication.class, args);
    }
}
