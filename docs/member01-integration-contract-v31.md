# Member 01 cross-module integration contract (V31 baseline)

Status: proposed integration policy and auth-side implementation; not a production approval. Baseline main 79c2a70a80066d8d00d9a46420a386eb02d16f19. No migration or hosted data change is authorized by this document.

## 02 + 01: customer contact changes and customer portal

The existing account proof is tied to the Customer email. A mere equality check is insufficient: changing A to B and back to A must not resurrect old identity proof, recovery challenges, or access tokens.

Member 01 provides the internal `CustomerContactSecurityService.invalidateBeforeEmailChange` contract. Member 02 owns the actual contact-update endpoint/service wiring; until that is merged and tested, the existing route is **not** covered by this hook.

For each contact update:
1. Authorize the actual validated JWT principal. The current email edit policy is OWNER with customer:manage (not CUSTOMER, CASHIER, or technical ADMIN). Never accept actor IDs/roles from the request body.
2. Start one READ_COMMITTED transaction. Load the managed Customer via `CustomerRepository.lockById(id)` before reading old contacts; compare normalized effective values.
3. For an email edit call `contactSecurity.invalidateBeforeEmailChange(customer, effectiveNewEmail, actor)` **before** changing the email. Blank/removal is null; omitted field means retain old value. Same case/whitespace-normalized email is a no-op for identity revocation.
4. Apply the profile edit and reset corresponding email/mobile verification and consent flags on genuine contact changes (Member 02 + 06); never carry consent to a different destination. Mobile-only changes do not invalidate email-based account proof under this proposed policy. Ensure notification worker revalidates destination and consent immediately before send; stale queued notifications must not be sent.
5. Commit together. Auth hook locks linked account, disables it, clears all customer proof/password setup markers and recovery email, replaces old password hash with an unknown encoded random placeholder, increments credential version, deletes pending challenges and appends an audit event. No direct mail sending. On rollback all changes revert.
6. A genuine email change requires approved fresh OWNER in-person proof, invitation, customer-chosen password and separate activation. Do not enable onboarding or SMTP for this test. Registration locks Customer before checking existing account; recovery and activation serialize on account. Do not introduce inverse account-to-Customer write lock paths without joint review.
7. Test A->B->A, blank/removal, no-op, no linked account, rollback, concurrent registration/recovery/activation, expired/old JWT, pending challenges, and both email/mobile consent resets on migrated isolated PostgreSQL. No production secrets or shared DB tests.

Customer self-service: exact GET routes only after ownership implementation/tests; use principal.customerId and exact CUSTOMER role / customer:self. Do not trust customer ID in path/body/query; use ownership-scoped queries for vehicle, appointment and history resources. Return only necessary customer-facing DTO fields; never expose staff notes/audits, cost/profit, internal metadata or another customer's records. Cross-customer attempts must fail without confirming resource existence. New routes default deny until review.

## 03 + 06 + 01: Employee UUID contract

- `garage.employees.id` and `AccountPrincipal.employeeId()` are UUID. `UserAccount.id` is a separate Long. A numeric account ID is NOT an employee ID.
- Proposed `assignedEmployeeId` and `technicianId` Java/JSON/DB types: java.util.UUID / UUID string / PostgreSQL UUID. No cast, hashing, truncation or invented mapping from existing BIGINT technician IDs.
- Member 03 owns Job Card entity, DTO, repository and schema alignment; Member 06 owns history DTO/entity/repository/report filter alignment. Both coordinate one additive migration plan, including foreign keys, legacy data inventory and explicit unmappable-row disposition. Do not edit V1–V31; do not assume every non-null numeric legacy value maps to an employee.
- Validate active employee and permitted technician assignment, enforce relevant FK, preserve historical references after deactivation, and test invalid/unknown UUID, inactive employee, repeated completion and authorization. No automatic technician login access.
- No version allocation here. Reserve only after team review. Application schema and runtime RLS/privilege/backup validation must be updated for any approved next version.

## 05 + 01: proposed billing authorization review

Current `billing:read` is OWNER-only, `billing:calculate` is individually CASHIER-delegatable. GET invoice collection and POST draft, issuance/cancellation, payment and ledger mutations stay default denied until exact endpoints, permission seeds, business eligibility, transaction/idempotency and acceptance tests are approved. Do not widen existing read to write, or grant CASHIER financial/bulk data by role. Member 05 provides exact proposed API/action matrix including read/create/issue/cancel/pay/ledger/export, allowed actors, object-level rules, audit fields, financial invariants and migration plan. Member 01 reviews and implements exact route matching and grant policy only after agreement. CUSTOMER and technical ADMIN cannot access staff billing routes.

## Release gates

Keep shared write freeze. No old migration edits, shared test connections, paid services, real owner credential changes or deployment. Member 02 hook wiring, actual PostgreSQL contact/recovery race and HTTP tests, Member 03/06 UUID mapping and schema migrations, Member 05 permission approval and all module acceptance are pending. Re-run the isolated CI pipeline after each merge. Durable encrypted archive/key custody and real OWNER handover still require operator sign-off.
