package com.careerai.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Registers {@link TestDatabaseGuard} for the automated-test profiles only. */
@Configuration
@Profile({ "test", "integration" })
public class TestDatabaseGuardConfig {

    // static: a BeanFactoryPostProcessor must be created before any other bean.
    @Bean
    public static TestDatabaseGuard testDatabaseGuard() {
        return new TestDatabaseGuard();
    }
}
