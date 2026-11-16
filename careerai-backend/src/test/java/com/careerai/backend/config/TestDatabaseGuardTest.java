package com.careerai.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Automated-test profiles must never reach the careerai database. */
class TestDatabaseGuardTest {

    @Test
    void recognisesTestDatabases() {
        assertThat(TestDatabaseGuard.isTestDatabase("jdbc:h2:mem:careerai_test;MODE=MySQL")).isTrue();
        assertThat(TestDatabaseGuard.isTestDatabase("jdbc:mysql://localhost:3306/careerai_test?createDatabaseIfNotExist=true")).isTrue();
        assertThat(TestDatabaseGuard.isTestDatabase("jdbc:mysql://localhost:3306/careerai?createDatabaseIfNotExist=true")).isFalse();
        assertThat(TestDatabaseGuard.isTestDatabase("jdbc:mysql://localhost:3306/careerai")).isFalse();
        assertThat(TestDatabaseGuard.isTestDatabase("jdbc:mysql://db.example.com/production")).isFalse();
        assertThat(TestDatabaseGuard.isTestDatabase("")).isFalse();
    }

    @Test
    void failsFastWhenTestProfileUsesCareeraiDatabase() {
        TestDatabaseGuard guard = guard("test", "jdbc:mysql://localhost:3306/careerai?serverTimezone=UTC");
        assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("careerai")
            .hasMessageContaining("_test");
    }

    @Test
    void failsFastWhenIntegrationProfileUsesCareeraiDatabase() {
        TestDatabaseGuard guard = guard("integration", "jdbc:mysql://localhost:3306/careerai");
        assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsTestDatabasesAndIgnoresOtherProfiles() {
        assertThatCode(() -> guard("test", "jdbc:h2:mem:careerai_test").postProcessBeanFactory(new DefaultListableBeanFactory())).doesNotThrowAnyException();
        assertThatCode(() -> guard("integration", "jdbc:mysql://localhost:3306/careerai_test").postProcessBeanFactory(new DefaultListableBeanFactory())).doesNotThrowAnyException();
        // The normal app (no test profile) is not affected.
        assertThatCode(() -> guard(null, "jdbc:mysql://localhost:3306/careerai").postProcessBeanFactory(new DefaultListableBeanFactory())).doesNotThrowAnyException();
    }

    private static TestDatabaseGuard guard(String profile, String url) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", url);
        if (profile != null) environment.setActiveProfiles(profile);
        TestDatabaseGuard guard = new TestDatabaseGuard();
        guard.setEnvironment(environment);
        return guard;
    }
}
