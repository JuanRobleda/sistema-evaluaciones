package com.universidad.tests.config.gen;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("gen02-draft")
@EnableScheduling
public class Gen02Configuration {
    @Bean
    Clock gen02Clock() {
        return Clock.systemUTC();
    }
}
