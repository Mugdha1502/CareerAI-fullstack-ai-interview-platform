package com.careerai.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fails fast when an automated-test profile ("test" or "integration") points at a database that
 * is not clearly a test database — e.g. the normal careerai development database.
 *
 * Runs as a BeanFactoryPostProcessor, i.e. before the DataSource and JPA (and therefore before
 * any ddl-auto create-drop) exist, so a misconfigured test run can never touch real data.
 * Allowed: in-memory H2 (jdbc:h2:mem:...) or a JDBC database whose name ends with "_test".
 */
public class TestDatabaseGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final Set<String> GUARDED_PROFILES = Set.of("test", "integration");
    private static final Pattern JDBC_DATABASE = Pattern.compile("^jdbc:[a-z0-9]+://[^/]+/([^?;]+)", Pattern.CASE_INSENSITIVE);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        boolean guarded = Arrays.stream(environment.getActiveProfiles()).anyMatch(GUARDED_PROFILES::contains);
        if (!guarded) return;

        String url = environment.getProperty("spring.datasource.url", "");
        if (!isTestDatabase(url)) {
            throw new IllegalStateException("Refusing to start: profile " + Arrays.toString(environment.getActiveProfiles())
                + " must use an in-memory H2 database or a database whose name ends with _test, but spring.datasource.url points at '"
                + databaseName(url) + "'. Automated tests must never use the careerai database.");
        }
    }

    /** True for jdbc:h2:mem:... or a JDBC database named *_test. */
    public static boolean isTestDatabase(String url) {
        String value = url == null ? "" : url.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:")) return true;
        String name = databaseName(value);
        return name != null && name.toLowerCase(Locale.ROOT).endsWith("_test");
    }

    static String databaseName(String url) {
        Matcher matcher = JDBC_DATABASE.matcher(url == null ? "" : url.trim());
        return matcher.find() ? matcher.group(1) : null;
    }
}
