# Member 01 — V31 engineering handover (27 Nov 2026)

**Status:** Member 01 core code and isolated acceptance delivered; cross-module and business release gates remain open. Not production certification. This is the current handover, superseding the status claims in [the historical V28 handover](member1-handover-20261118.md). Its API inventory and local demo instructions remain useful; use current main and do not use historical version-locked operational scripts.

## Verified baseline and scope

- Main code baseline: `37eab74e608f48cd4e2d6cb5d5130f69c96e6fba`, [merged PR #84](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/84). Exact premerge validated head `e4dda452b7188b9d5af8f3b44b3744d3157beba1`; [CI 45933001219](https://github.com/samudithaasansa3492/richcomes-automobile-backend/actions/runs/45933001219) both Java/PostgreSQL/Flyway and poisoned-environment jobs SUCCESS. Tested tree matches merge tree `553bb7521608160516268cb8276d76233421f3ab`. A separate postmerge run is not asserted here.
- 647 selected regression tests and nine isolated PostgreSQL full-context tests reported passing, including customer email-change identity revocation. Fresh/upgrade V1–V31, immutable migration source, 34-table isolated restore, restricted runtime access and private Compose synthetic OWNER/CASHIER smoke validated. Counts refer to that code baseline, not all end-to-end business scenarios.
- Employee UUID CRUD/lifecycle, account CRUD/lifecycle, last-owner protection, BCrypt, OWNER/CASHIER grants, technical ADMIN separation, CUSTOMER proofing gates, RS256 15-minute JWT and database-reloaded effective permissions are delivered. See [account management](account-management.md) and [first OWNER provisioning](first-admin-provisioning.md).
- PR #84 supplies transactional customer contact identity revocation hook, not actual contact-update endpoint wiring. Read the [cross-module contract](member01-integration-contract-v31.md).

## Database and recovery evidence

See [incident #75](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/75) for chronological operator evidence. The previous damaged project is preserved; lost historic rows were not recovered. Replacement hosted application schema was constructed with actual Flyway V1–V31. The verified recovery baseline contains 31 successful migrations, 34 application tables, 4 roles, 45 permissions and 122 runtime policies. Runtime is not a database owner and DDL/truncate are denied. Shared test connections are prohibited; direct runtime DML still requires correct application authorization.

- Hosted auth smoke passed startup/readiness, OWNER login, 15-minute JWT, identity/employee/account access, invalid password, missing/tampered JWT and customer-only route denial. The temporary backend was stopped. The enabled OWNER was synthetic; real-owner transfer is not completed.
- 25 Nov V31 snapshot source code commit `79c2a70a80066d8d00d9a46420a386eb02d16f19`: encrypted offsite upload, independent retrieval/decryption, isolated PostgreSQL restore, Flyway history and all 34 application table counts verified. Runtime privileges and policies reconstructed and tested independently. Scope is garage schema + public Flyway history, not all Supabase platform resources.
- [Offline export evidence](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/75#issuecomment-7209201341): independent encrypted copy of the same snapshot, not a new source export.
- [Laptop checksum evidence](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/75#issuecomment-7213969603) and [vault/decryption evidence](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/75#issuecomment-7217105794): operator reports successful Windows transfer, SHA-256 validation, encrypted KeePassXC vault storage/readback, and full AGE decrypt. No plaintext backup file produced by verification. Laptop and vault are on one physical device; a second independently protected vault copy is recommended. Retain original key and offsite ciphertext; do not commit credentials, keys, vault files or dumps.
- Snapshot is historical V31 data, not a backup of future writes or code changes. Before an approved migration or real-owner change, recheck live history, confirm backup/rollback plan and refresh verified snapshot at the appropriate rollout stage.

## Team integration gates

| Owner | Remaining acceptance | Member 01 dependency |
| --- | --- | --- |
| 02 | Actual contact update hook wiring; consent reset; customer-owned portal DTO/queries; appointment-job linking and cancellation/completion races | Review exact JWT and object ownership paths; test transactional identity invalidation and old JWT denial |
| 03 | Service category/job entity-schema parity; safe job number and transition handling; appointment linkage | Employee UUID and active assignment contract; no numeric account ID substitution |
| 04 | Inventory acceptance including GRN rollback, duplicate requests, stock concurrency and job-part stock integration | Review exact permission requirements, dependent on 03 |
| 05 | Invoice duplicate protection, issue/cancel, payments/ledger and approved financial invariants | Exact OWNER/CASHIER permission matrix before routes are opened |
| 06 | History employee UUID and actual costs/invoice; notification provider delivery and consent checks; report semantics | Review notification permissions/identity, coordinate 02/03/05 |

No V32+ number reserved by this handover. Use [registry #68](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/68); confirm local/unpushed claims with authors, review additive SQL and dependencies, and update version-locked runtime/recovery tooling for approved versions. Preserve V1–V31.

## Release acceptance

- Keep shared write freeze; no deployment, new paid resources or shared database test runs authorized.
- [Customer onboarding #32](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/32): business proofing SOP, HTTPS customer setup, SMTP/consent and release authorization outstanding; keep onboarding disabled.
- [Business permissions #33](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/33): exact module owner sign-off outstanding; default deny unapproved routes/actions.
- [Release #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34): real OWNER controlled handover, retirement of synthetic owner without violating last-owner protection, all module acceptance, current backup/recovery proof, approved hosting configuration/cost, and explicit rollout approval. Do not treat staging or hosted local smoke as public Railway deployment.
- Assignment rubric acceptance is not independently established; obtain team/lecturer confirmation of any additional requirements.

For team demo use the historical handover's private local Compose instructions with current source-latest main. Never point test or demo commands at shared Supabase. Test and CI evidence must be refreshed after changes; no claim of complete production readiness.

**Review decision:** accept Member 01 core engineering handover, or name a specific missing rubric/API criterion. Keep remaining module and release gates explicitly open.
