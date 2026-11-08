package com.example.claims.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A Clock bean instead of Instant.now(), so time-based logic (coverage, claim numbers) is testable. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
