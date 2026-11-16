# Flyway history alignment — 2026-11-16

## Scope and safety

This change is source-only. **No SQL was applied to shared Supabase, no migration history table was modified, and no production application startup was performed.**

A read-only inspection of the project `richcomes-automobile-dev` confirmed that `public.flyway_schema_history` contains successful versions **V1 through V14**, including:

| Applied version | Script; keep both filename and SQL unchanged |
| --- | --- |
| V12 | `V12__create_service_reminders.sql` |
| V13 | `V13__create_job_items_and_parts.sql` |
| V14 | `V14__create_invoice_tables.sql` |

The three applied scripts remain byte-for-byte unchanged in this branch.

## Unapplied scripts moved to unique later versions

| New version | Script | Former conflicting version |
| --- | --- | --- |
| V15 | `V15__inventory_decimal_quantities.sql` | V14 |
| V16 | `V16__add_stock_item_unit.sql` | V15 |
| V17 | `V17__create_identity_and_access_tables.sql` | V12 |
| V18 | `V18__seed_member_one_roles_and_permissions.sql` | V13 |
| V19 | `V19__separate_owner_and_technical_admin.sql` | V14 |
| V20 | `V20__technical_identities_and_recovery.sql` | V15 |

Only filenames and operational docs were changed for these scripts. Each pending script's SQL content is preserved; migration semantics have **not** been independently reverified against the newly combined main branch.

Do not restore the former duplicate filenames, run `flyway repair`, edit applied versions, or call `flyway migrate` on the shared development database merely to clear validation errors.

## Mandatory verification before merge/rollout

1. Re-check each target environment's `flyway_schema_history` read-only. If any environment has applied one of the formerly colliding Member 1/inventory scripts under V12–V15, this version plan is not immediately compatible with that environment; stop and reconcile individually.
2. Confirm that the tracked applied V1–V14 filenames/descriptions/checksums match the target environment's history. Verify no manual schema drift.
3. In a **disposable** PostgreSQL 17 database, run an empty-database migration through V20 and `flyway validate`. Inspect roles, access grants/RLS, column types and constraints.
4. On a separate **disposable** PostgreSQL 17 database, recreate the applied V1–V14 baseline in its original order, then upgrade through V15–V20 and validate. Check that existing synthetic customers/jobs/invoices remain readable and role changes are correct.
5. Run Java 21 compile, selected tests and full multi-module integration/authorization tests. Prior 153 passing Member 1 tests were from a different migration-version layout and are **not** evidence that this new combined migration sequence is verified.
6. Obtain explicit owner/team approval and a backup/change-window plan before any shared Supabase write or application start with Flyway enabled. Account for deployed app compatibility and migration ordering.

## Additional system integration blockers

- The application currently ends authorization rules with `.anyRequest().denyAll()`; other members' Customer/Vehicle/Job/Billing routes have no agreed allowlist. Do not make these APIs public indiscriminately.
- The `@WebMvcTest` Customer/Vehicle controller tests currently fail during context startup because `AccountJwtAuthenticationConverter` requires `UserAccountRepository` outside the slice. Test configuration and production permissions require separately reviewed changes.
- The approved CUSTOMER identity/onboarding and per-CASHIER delegated `customer:register` permission are new capabilities, not implemented by renumbering Flyway SQL.
- `.gitignore` now excludes local `.env` variants. This does not remove any file previously tracked or exposed in an archive; verify Git history and rotate any actually exposed credentials through the agreed security process.
