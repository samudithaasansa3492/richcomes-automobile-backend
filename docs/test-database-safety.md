# Test database safety — P0 incident #75

Shared database is quarantined pending recovery. Do not use old checkout test commands with shared credentials.

- Default Maven/IDE unit and slice tests use a fixed H2 memory database or explicit embedded H2 fixtures; the test classpath selects the test default before configuration import. Generic DB_URL, DB_USERNAME, DB_PASSWORD, DB_DIALECT and FLYWAY_ENABLED cannot select a real DB in the H2 profile.
- Full Spring application context tests are opt-in disposable PostgreSQL only. Run `bash scripts/validate-spring-context-isolated.sh` in a clean checkout without .env, with Java 21 and Docker. It provisions a random localhost-only port, synthetic credentials, test keys, migrations and cleanup. The ordinary Maven test suite intentionally skips these three opt-in classes; CI still explicitly runs them.
- The isolated-pg profile requires runner opt-in, exact loopback JDBC shape and PostgreSQL dialect, with Hibernate DDL disabled. Do not use a port forwarding tunnel to a shared DB.
- Startup safety guard runs before ordinary datasource/Hibernate/Flyway initialization. Direct external overrides, alternate targets and non-test Hibernate schema mutations fail closed; do not bypass it. Production Flyway remains disabled by default. Test profiles do not import .env.
- Direct embedded auth fixtures use the garage default schema and halt on DDL errors; no shared database connections are used.
- This is prevention, not data recovery. Preserve Flyway history and obtain a verified pre-incident backup; restore to a separate authorized target first. Do not run repair, clean, reapply migrations, reset, or restore in place until the owner approves a recovery plan.
