package io.github.chikachi.ticketforge.demo.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo & !prod")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoConfiguration {
}
