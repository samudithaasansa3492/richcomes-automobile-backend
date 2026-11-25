package com.richcomes.garage.config;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/** Reject unsafe database configuration before a DataSource, Flyway or Hibernate can start. */
public final class DatabaseSafetyGuard implements EnvironmentPostProcessor, Ordered {
    static final String H2_URL = "jdbc:h2:mem:garage_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM 'classpath:init-garage-schema.sql'";
    private static final String TEST_MARKER = "META-INF/richcomes-test-runtime.marker";

    private static boolean testRuntime() {
        return DatabaseSafetyGuard.class.getClassLoader().getResource(TEST_MARKER) != null;
    }

    /** Set the default before ConfigData, including unprofiled MVC slices in IDEs. */
    public static final class TestDefaults implements EnvironmentPostProcessor, Ordered {
        @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER - 1; }
        @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
            if (testRuntime()) {
                if (env.getActiveProfiles().length > 0
                        && !env.acceptsProfiles(Profiles.of("test", "isolated-pg")))
                    fail("External runtime profiles are prohibited on the test classpath");
                env.getPropertySources().addFirst(new MapPropertySource("richcomes-test-defaults",
                        Map.of("spring.profiles.default", "test")));
            }
        }
    }

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        verify(env, testRuntime(), "YES".equals(System.getenv("RICHCOMES_LOCAL_PG_TESTS")));
    }

    /** Recheck after test property sources and before ordinary singleton initialization. */
    public static final class FinalCheck implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            context.addBeanFactoryPostProcessor(factory -> verify(context.getEnvironment(),
                    testRuntime(), "YES".equals(System.getenv("RICHCOMES_LOCAL_PG_TESTS"))));
        }
    }

    static void verify(Environment env, boolean testRuntime, boolean pgOptIn) {
        boolean h2 = env.acceptsProfiles(Profiles.of("test"));
        boolean pg = env.acceptsProfiles(Profiles.of("isolated-pg"));
        if (h2 && pg) fail("Choose exactly one test database profile");
        if ((h2 || pg) && !testRuntime) fail("Test database profiles require the test classpath");
        if (testRuntime && !h2 && !pg) fail("Test runtime requires test or isolated-pg profile");
        String ddl = property(env, "spring.jpa.hibernate.ddl-auto", "none");
        String alias = property(env, "spring.jpa.properties.hibernate.hbm2ddl.auto", "none");
        String jpaAction = property(env, "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none");
        String legacyAction = property(env, "spring.jpa.properties.javax.persistence.schema-generation.database.action", "none");
        if (env.getProperty("spring.jpa.generate-ddl", Boolean.class, false))
            fail("Implicit JPA schema generation is prohibited");
        if (!Set.of("none", "validate").contains(alias) || !"none".equals(jpaAction) || !"none".equals(legacyAction))
            fail("Direct Hibernate/JPA schema generation overrides are prohibited");
        if (!h2 && !Set.of("none", "validate").contains(ddl))
            fail("Hibernate schema mutation is prohibited outside isolated H2");
        if (!testRuntime) return;
        String url = env.getProperty("spring.datasource.url", "");
        String driver = env.getProperty("spring.datasource.driver-class-name", "");
        String dialect = env.getProperty("spring.jpa.properties.hibernate.dialect", "");
        for (String key : Set.of("spring.datasource.jndi-name", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-jndi",
                "spring.flyway.url", "spring.datasource.type", "spring.jpa.properties.hibernate.connection.url",
                "spring.jpa.properties.hibernate.connection.datasource",
                "spring.jpa.properties.hibernate.connection.provider_class",
                "spring.jpa.properties.javax.persistence.jdbc.url",
                "spring.jpa.properties.jakarta.persistence.jdbc.url")) {
            if (!env.getProperty(key, "").isBlank()) fail("Alternate database targets are prohibited in tests");
        }
        String platform = env.getProperty("spring.jpa.database-platform", "");
        if (!platform.isBlank() && !platform.equals(dialect)) fail("Conflicting JPA database platform");
        if (!"never".equals(property(env, "spring.sql.init.mode", "never")))
            fail("External SQL initialization is prohibited in tests");
        if (h2) {
            if (!H2_URL.equals(url) || !"org.h2.Driver".equals(driver)
                    || !"org.hibernate.dialect.H2Dialect".equals(dialect)
                    || !"sa".equals(env.getProperty("spring.datasource.username", ""))
                    || !env.getProperty("spring.datasource.password", "").isEmpty()
                    || env.getProperty("spring.flyway.enabled", Boolean.class, false)
                    || !Set.of("none", "validate", "create-drop").contains(ddl))
                fail("H2 tests require the fixed in-memory database and matching H2 settings");
        } else {
            if (!pgOptIn || !url.matches("^jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/richcomes_context[?]sslmode=disable$")
                    || !"org.postgresql.Driver".equals(driver)
                    || !"org.hibernate.dialect.PostgreSQLDialect".equals(dialect)
                    || !"postgres".equals(env.getProperty("spring.datasource.username", ""))
                    || !env.getProperty("spring.datasource.password", "").matches("synthetic-[0-9]+-[0-9]+-[0-9]+")
                    || !"none".equals(ddl))
                fail("PostgreSQL tests require explicit disposable localhost runner and ddl-auto=none");
        }
    }
    private static String property(Environment env, String key, String fallback) {
        return env.getProperty(key, fallback).strip().toLowerCase(Locale.ROOT);
    }
    private static void fail(String reason) {
        // Do not echo JDBC URLs or credentials into logs.
        throw new IllegalStateException("DATABASE SAFETY STOP: " + reason);
    }
}
