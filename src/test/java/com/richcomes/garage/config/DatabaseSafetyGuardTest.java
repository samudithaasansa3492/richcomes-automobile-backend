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
                .withProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .withProperty("spring.jpa.hibernate.ddl-auto", "none");
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
