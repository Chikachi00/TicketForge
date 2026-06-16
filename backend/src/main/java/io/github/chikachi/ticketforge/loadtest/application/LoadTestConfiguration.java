package io.github.chikachi.ticketforge.loadtest.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("loadtest")
@Configuration
@EnableConfigurationProperties(LoadTestProperties.class)
public class LoadTestConfiguration {
}
