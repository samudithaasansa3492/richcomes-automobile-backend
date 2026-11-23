package com.richcomes.garage.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuse to expose the current API against a pre-V23 schema.
 * A healthy DB connection alone does not mean the schema supports this release.
 * This checks PostgreSQL metadata without requiring the runtime role to
 * read or mutate the protected public.flyway_schema_history table.
 * <p>
 * This guard is only active for PostgreSQL (production-like) environments.
 * It is disabled for the 'test' profile which uses H2.
 */
@Component
@Profile("!test")
public final class SchemaCompatibilityStartupGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SchemaCompatibilityStartupGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer columnCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT attname)
                FROM pg_attribute
                WHERE attrelid = to_regclass('garage.user_accounts')
                  AND attnum > 0 AND NOT attisdropped
                  AND attname IN ('customer_verified_at', 'customer_verified_by',
                      'customer_verification_ref', 'customer_verified_email',
                      'customer_password_set_at')
                """, Integer.class);
        Boolean initialChallengeAllowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conrelid = to_regclass('garage.recovery_challenges')
                      AND conname = 'recovery_challenges_purpose_check'
                      AND pg_get_constraintdef(oid) LIKE '%INITIAL_PASSWORD%'
                )
                """, Boolean.class);
        if (columnCount == null || columnCount != 5 || !Boolean.TRUE.equals(initialChallengeAllowed)) {
            throw new IllegalStateException(
                    "Richcomes schema is not compatible with this API release. "
                    + "Apply reviewed V15-V23 separately after verified backup/restore. "
                    + "Never turn on Flyway in the normal application service to bypass this guard.");
        }
    }
}
