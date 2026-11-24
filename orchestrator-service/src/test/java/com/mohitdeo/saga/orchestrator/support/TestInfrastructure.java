package com.mohitdeo.saga.orchestrator.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestInfrastructure {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-01T09:00:00Z"));
    }

    /** One fake registered as the primary bean for all three participant interfaces. */
    @Bean
    @Primary
    FakeParticipants fakeParticipants() {
        return new FakeParticipants();
    }
}
