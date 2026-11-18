# CUSTOMER identity proofing — operational SOP for garage approval

> **Status:** proposed staff procedure and acceptance checklist, NOT evidence that any real claimant has been verified. Complete training, case-system retention/access controls and independent approval before setting `CUSTOMER_ONBOARDING_ENABLED=true`. Code cannot verify a physical government ID or establish mailbox ownership by itself.

## Roles and separation

- **Business record creator (authorized staff):** confirms that the garage Customer record already exists, is ACTIVE, and is correctly linked to the actual customer. Staff do not select initial customer passwords.
- **OWNER verifier:** personally verifies the claimant against an authentic physical ID *in person*, checks the person/photo match and pre-existing Customer record, and independently confirms that the customer-controlled mailbox on that record belongs to the claimant. A CASHIER can never attest verification, issue an invitation or activate an account.
- **Privacy/evidence custodian:** maintains a restricted, independently auditable case record and an approved retention/deletion policy. Raw document numbers, copies, photographs, passwords and tokens must not enter this API, request logs, case-reference strings, email templates or publicly readable audit events.
- **CUSTOMER:** alone sets the initial password through an HTTPS page and single-use challenge. OWNER cannot read or pick it. The account stays disabled until OWNER activates.

## In-person enrollment checklist

1. Confirm the identity proofing process is approved and operators trained. Verify the claimant is present at the garage. Do **not** proceed through a phone call, unverified message or an employee's assumption.
2. Open the existing garage Customer record using an authorized staff workflow; confirm the record status is ACTIVE. Search for duplicates and existing linked user accounts. Suspended, inactive, disputed or duplicate records stop the process.
3. Compare authentic physical proof with the claimant and the garage's pre-existing information according to the business-approved identification standard. Reject ambiguous matches, mismatched names/details, suspected forgeries or coercion. Do not store or capture raw ID numbers or images in the customer API or GitHub.
4. Verify that the email on the garage record is directly controlled by the claimant. If the recorded email is disputed, verify/change it through a separate approved customer-data procedure **before** inviting. Email mailbox access alone is not sufficient proof of real-world identity.
5. Open a case in an access-controlled evidence registry. Record only approved minimal metadata there: case reference, Customer/account identifiers, method, authorized verifier, time, outcome, consent, evidence handling/retention classification and any independently approved second-person review. Do not place the reference's raw underlying evidence into logs or API payloads.
6. Authorized staff enrolls the **existing** Customer as disabled via `POST /api/customer-accounts` with `{"customerId":12,"username":"customer.one"}`; the request has no password field. Abort on conflict or a pre-existing account.
7. OWNER, after examining the restricted case, calls `POST /api/customer-accounts/{id}/verify-and-invite` with `{"caseReference":"CASE-00123"}`. The case reference must be 8–64 uppercase ASCII letters/digits/underscore/hyphen; no NIC, email, customer name or personal ID in the reference.
8. The invite is a 15-minute single-use token sent only to the confirmed Customer record email through approved TLS SMTP. A customer visits the same-origin HTTPS setup page, chooses a 15–72-character password (max 72 UTF-8 bytes), and sends the token and password directly to the backend. **Staff must not collect, relay or log the secret.**
9. After verifying the case is still valid, the Customer is active and the customer-chosen password is recorded, OWNER alone calls `PATCH /api/customer-accounts/{id}/activate`. Confirm subsequent login is restricted to `GET /api/customer/me`; never grant staff authorities to customer identities.
10. Log the case outcome and securely close the case. The independently approved record custodian sets retention, access restrictions, secure disposal and incident-response procedures. Never use a made-up case reference or skip physical verification for a demo and claim production readiness.

## Denial, revocation and support

- **Invalid, expired, replayed or wrong-purpose link:** deny; request a new, freshly verified invitation. Do not reveal whether an account exists; avoid provider errors in responses.
- **Customer suspended, email changed, account disabled, or case disputed:** stop/revoke access and invitations under the approved policy; escalate to OWNER and custodian. Re-verification is required for a changed email; current account authentication policy rejects proof if the email no longer matches.
- **Lost credentials or identity dispute:** follow an approved independent recovery process. Do not use staff-selected replacement passwords or share plaintext credentials over chat/email. Record actor, time, reason and disposition without revealing token/password content.
- **Suspected impersonation:** suspend affected business Customer through the authorized customer-management workflow and preserve restricted audit evidence. A suspended Customer cannot authenticate or continue using existing bearer access under current account policy.
- **Sensitive admin/billing access:** never granted to CUSTOMER; CASHIER grants are individually approved and auditable; module-specific customer PII and outbound-message consent must be reviewed separately.

## Release acceptance (keep pending until genuinely evidenced)

- [ ] Business owner approves the precise physical ID standard, verifier training and escalation steps.
- [ ] Evidence custodian demonstrates an access-controlled case registry, retention policy, and permitted reference format.
- [ ] Approved HTTPS origin serves `/account-setup.html`, compatible with all three SMTP recovery purposes.
- [ ] Reviewed SMTP sender/recipient/domain, no uncontrolled mail logs, tested resend/expiry/replay and abuse safeguards.
- [ ] Shared DB V15–V23 rollout approved with a retrieved, encrypted off-site backup and independently tested restore.
- [ ] Independent security reviewer validates the workflow and verifies a real case, without copying raw ID documents into CI.
- [ ] Record sign-off from the actual accountable business/security owners. A general chat approval to code changes is not evidence that these external controls have been deployed.

Related: [Issue #32](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/32), [verification API](verified-customer-onboarding.md), [shared DB release gate](shared-supabase-backup-restore.md).
