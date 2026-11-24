package com.richcomes.garage.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSafetyGuardTest {
    private MockEnvironment h2() {
        var e = new MockEnvironment();
        e.setActiveProfiles("test");
        return e.withProperty("spring.datasource.url", DatabaseSafetyGuard.H2_URL)
                .withProperty("spring.datasource.driver-class-name", "org.h2.Driver")
                .withProperty("spring.datasource.username", "sa")
                .withProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop")
                .withProperty("spring.flyway.enabled", "false");
    }
    private MockEnvironment pg() {
        var e = new MockEnvironment();
        e.setActiveProfiles("isolated-pg");
        return e.withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:54321/richcomes_context?sslmode=disable")
                .withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver")
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "synthetic-1-2-3")
                .withProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .withProperty("spring.jpa.hibernate.ddl-auto", "none");
    }
    @Test void registeredGuardRejectsUnsafeBootStartupBeforeAnyBeanCreation() {
        var app = new org.springframework.boot.SpringApplication(EmptyApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        var failure = assertThrows(Exception.class, () -> {
            try (var ignored = app.run("--spring.profiles.active=test",
                    "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/forbidden",
                    "--spring.jpa.hibernate.ddl-auto=create-drop")) {
                fail("Unsafe configuration unexpectedly started");
            }
        });
        boolean guarded = false;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("DATABASE SAFETY STOP")) {
                guarded = true; break;
            }
        }
        assertTrue(guarded, "Expected registered pre-start safety guard");
    }
    static class EmptyApplication {}

    @Test void defaultBootTestProfileIgnoresGenericEnvAndLocalDotenv() throws Exception {
        var app = new org.springframework.boot.SpringApplication(EmptyApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        try (var ctx = app.run()) {
            var env = ctx.getEnvironment();
            assertTrue(env.acceptsProfiles(org.springframework.core.env.Profiles.of("test")));
            assertEquals(DatabaseSafetyGuard.H2_URL, env.getProperty("spring.datasource.url"));
            assertEquals("org.h2.Driver", env.getProperty("spring.datasource.driver-class-name"));
            assertEquals("sa", env.getProperty("spring.datasource.username"));
            assertEquals("", env.getProperty("spring.datasource.password"));
            assertFalse(env.getProperty("spring.flyway.enabled", Boolean.class, true));
            assertNull(env.getProperty("richcomes.test.dotenv-marker"),
                    "Local .env must not be imported on test classpath");
            try (var connection = java.sql.DriverManager.getConnection(DatabaseSafetyGuard.H2_URL, "sa", "")) {
                assertEquals("H2", connection.getMetaData().getDatabaseProductName());
            }
        }
    }

    @Test void fixedH2IgnoresGenericProductionEnvironmentVariables() {
        assertDoesNotThrow(() -> DatabaseSafetyGuard.verify(h2().withProperty("DB_URL", "jdbc:postgresql://external.invalid/postgres")
                .withProperty("DB_PASSWORD", "not-a-real-secret").withProperty("FLYWAY_ENABLED", "true"), true, false));
    }
    @Test void refusesDirectExternalDatasourceOverrideEvenWithDdlNone() {
        var e = h2().withProperty("spring.datasource.url", "jdbc:postgresql://external.invalid/postgres")
                .withProperty("spring.jpa.hibernate.ddl-auto", "none");
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(e, true, false));
    }
    @Test void refusesDestructiveProductionModesBeforeConnection() {
        for (String mode : new String[]{"create", "create-drop", "update", "drop", "create-only"}) {
            var e = new MockEnvironment().withProperty("spring.jpa.hibernate.ddl-auto", mode);
            assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(e, false, false));
        }
    }
    @Test void isolatedPostgresRequiresOptInExactTargetAndMatchingDialect() {
        assertDoesNotThrow(() -> DatabaseSafetyGuard.verify(pg(), true, true));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg(), true, false));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.datasource.password", "not-generated-by-runner"), true, true));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect"), true, true));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.datasource.url", "jdbc:postgresql://external.invalid/postgres"), true, true));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.jpa.hibernate.ddl-auto", "create-drop"), true, true));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.H2Dialect"), true, true));
    }
    @Test void rejectsAlternativeTargetsAndSchemaGenerationAliases() {
        for (String key : new String[]{"spring.flyway.url", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.jndi-name", "spring.jpa.properties.hibernate.connection.url"}) {
            assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(h2().withProperty(key, "external"), true, false));
        }
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(pg().withProperty("spring.jpa.properties.hibernate.hbm2ddl.auto", "update"), true, true));
    }
    @Test void rejectsProductionProfileOnTestClasspathAndTestProfileInPackagedApp() {
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(new MockEnvironment(), true, false));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyGuard.verify(h2(), false, false));
    }
}
