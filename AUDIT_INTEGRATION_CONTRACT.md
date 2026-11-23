# Cross-Module Audit Integration Contract

**Member 06 — Richcomes Automobile Garage Management System**

This document defines the audit integration contract between Member 06 (Audit, History, Reminders, Notifications, Reports) and other module owners.

---

## 1. Existing Audit Service Interface

### 1.1 Core Service: `AuditLogService`

```java
@Service
@Transactional
public class AuditLogService {

    /**
     * Records an audit event from a trusted backend service.
     *
     * @param actorUserId    ID of the authenticated user or system actor (must be > 0)
     * @param action         Action name (normalized to UPPERCASE, max 80 chars)
     * @param entityType     Entity type name (max 80 chars)
     * @param entityId       Entity ID (must be > 0)
     * @param oldValue       Previous state snapshot (nullable)
     * @param newValue       New state snapshot (nullable)
     * @param summary        Human-readable summary (required)
     * @param ipAddress      Client IP address (nullable, max 50 chars)
     * @return persisted AuditLog entity
     */
    public AuditLog logEvent(Long actorUserId, String action, String entityType,
                             Long entityId, String oldValue, String newValue,
                             String summary, String ipAddress)
}
```

### 1.2 Query Methods

```java
// Paginated audit log retrieval
Page<AuditLogResponse> getAllAuditLogs(int page, int size);

// Single audit log by ID
AuditLogResponse getAuditLogById(Long id);

// Logs for specific entity
List<AuditLogResponse> getAuditLogsByEntity(String entityType, Long entityId);

// Logs by actor
List<AuditLogResponse> getAuditLogsByActor(Long actorUserId);

// Logs by action
List<AuditLogResponse> getAuditLogsByAction(String action);

// Logs by date range
List<AuditLogResponse> getAuditLogsByDateRange(LocalDateTime start, LocalDateTime end);
```

### 1.3 AuditLog Entity Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `actorUserId` | Long | Authenticated user ID or system actor (0 = system) |
| `action` | String | Normalized UPPERCASE action (CREATE, UPDATE, DELETE, etc.) |
| `entityType` | String | Entity type name (SERVICE_HISTORY, SERVICE_REMINDER, NOTIFICATION, etc.) |
| `entityId` | Long | Target entity ID |
| `oldValue` | String | Previous state (TEXT) |
| `newValue` | String | New state (TEXT) |
| `summary` | String | Human-readable description |
| `timestamp` | LocalDateTime | Event timestamp |
| `ipAddress` | String | Client IP (nullable) |

---

## 2. Member 06 Audit Integration Points

Member 06 automatically audits the following operations:

### 2.1 Service History (`entityType: SERVICE_HISTORY`)

| Operation | Action | Actor | oldValue | newValue |
|-----------|--------|-------|----------|----------|
| `recordServiceHistory()` | CREATE | 0 (system) | null | Full snapshot |
| `updateServiceHistory()` | UPDATE | Authenticated user | Snapshot before | Snapshot after |
| `deleteServiceHistory()` | DELETE | Authenticated user | Snapshot before | null |

### 2.2 Service Reminders (`entityType: SERVICE_REMINDER`)

| Operation | Action | Actor | oldValue | newValue |
|-----------|--------|-------|----------|----------|
| `createReminder()` | CREATE | 0 (system) | null | vehicleId, customerId, dates |
| `generateFromServiceHistory()` | CREATE | 0 (system) | null | vehicleId, customerId, serviceHistoryId, dates |
| `sendReminder()` | NOTIFICATION_QUEUED | 0 (system) | null | notificationId, channel, masked recipient |
| `cancelReminder()` | CANCEL | 0 (system) | status=PENDING | status=CANCELLED |
| `updateReminder()` | UPDATE | 0 (system) | Snapshot before | Snapshot after |

### 2.3 Notifications (`entityType: NOTIFICATION`)

| Operation | Action | Actor | oldValue | newValue |
|-----------|--------|-------|----------|----------|
| `sendNotification()` | CREATE | 0 (system) | null | type, channel, masked recipient, status |
| `retryNotification()` | RETRY | 0 (system) | status, failureReason, retryCount | status, failureReason, retryCount |

### 2.4 Snapshot Format

```text
# Service History
odometer=50000, summary=Oil change, invoiceId=123, totalCost=15000.00, nextServiceDate=2024-07-15

# Service Reminder
vehicleId=10, customerId=1, nextServiceDate=2024-07-15, reminderDate=2024-07-08

# Notification
type=SERVICE_REMINDER, channel=SMS, recipient=+94****7, status=PENDING
```

---

## 3. Trusted Actor Resolution

### 3.1 Authenticated Operations (Controllers)

```java
// ServiceHistoryController
private Long getAuthenticatedUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return Long.parseLong(auth.getName()); // JWT subject = user ID
}

// Usage in controller:
Long actorUserId = getAuthenticatedUserId();
String ipAddress = httpRequest.getRemoteAddr();
serviceHistoryService.updateServiceHistory(id, request, actorUserId, reason, ipAddress);
```

### 3.2 System-Generated Operations (Background Workers, Schedulers)

```java
// Use actorUserId = 0L for system-initiated events
auditLogService.logEvent(
    0L,                    // System actor
    "CREATE",
    "SERVICE_HISTORY",
    saved.getId(),
    null,
    historySnapshot(saved),
    "Service history created from job completion",
    null                   // No IP for system events
);
```

### 3.3 Rules

| Scenario | Actor ID | IP Address |
|----------|----------|------------|
| User-initiated via REST | JWT subject (user ID) | `HttpServletRequest.getRemoteAddr()` |
| Scheduled/background | `0L` (system) | `null` |
| Automatic from job completion | `0L` (system) | `null` |

---

## 4. Cross-Module Integration Contracts

### 4.1 Member 01 — Authentication & Security

**Required Audit Events from Member 01:**

| Event | Action | EntityType | Description |
|-------|--------|------------|-------------|
| User login | LOGIN | USER_ACCOUNT | Successful authentication |
| Failed login | LOGIN_FAILED | USER_ACCOUNT | Invalid credentials |
| Password change | PASSWORD_CHANGE | USER_ACCOUNT | User or admin initiated |
| Permission grant | PERMISSION_GRANT | USER_ACCOUNT | Role/permission assigned |
| Permission revoke | PERMISSION_REVOKE | USER_ACCOUNT | Role/permission removed |
| Account lock | ACCOUNT_LOCK | USER_ACCOUNT | Admin action |
| Account unlock | ACCOUNT_UNLOCK | USER_ACCOUNT | Admin action |

**Integration:**
```java
// In Member 01's AuthenticationService or controllers:
@Autowired private AuditLogService auditLogService;

public void onSuccessfulLogin(Long userId, String ip) {
    auditLogService.logEvent(userId, "LOGIN", "USER_ACCOUNT", userId,
        null, "authenticated=true", "User logged in", ip);
}
```

### 4.2 Member 02 — Customer, Vehicle, Appointment

**Required Audit Events from Member 02:**

| Event | Action | EntityType | Description |
|-------|--------|------------|-------------|
| Customer created | CREATE | CUSTOMER | New customer registered |
| Customer updated | UPDATE | CUSTOMER | Profile/contact changes |
| Customer status change | STATUS_CHANGE | CUSTOMER | ACTIVE/SUSPENDED/DELETED |
| Vehicle created | CREATE | VEHICLE | Vehicle registered |
| Vehicle updated | UPDATE | VEHICLE | Details/mileage changes |
| Appointment created | CREATE | APPOINTMENT | New booking |
| Appointment status change | STATUS_CHANGE | APPOINTMENT | PENDING/APPROVED/DONE/CANCELLED |

**Contact Information:** Member 02 must add consent/verification fields for Member 06 phone resolver:
```java
// Required on Customer entity:
boolean smsConsent = false;          // Member 06 needs this
boolean mobileVerified = false;      // Member 06 needs this
boolean emailConsent = false;        // Future email notifications
boolean emailVerified = false;       // Future email notifications
```

### 4.3 Member 03 — Jobs

**Required Audit Events from Member 03:**

| Event | Action | EntityType | Description |
|-------|--------|------------|-------------|
| Job created | CREATE | SERVICE_JOB | New service job |
| Job status change | STATUS_CHANGE | SERVICE_JOB | PENDING/IN_PROGRESS/COMPLETED/CLOSED/CANCELLED |
| Job finalized | FINALIZE | SERVICE_JOB | Job closed, triggers Member 06 history |

**Critical Integration:** Member 03 must call Member 06 after job finalization:
```java
// In ServiceJobServiceImpl.updateJobStatus() when status = COMPLETED/CLOSED:
@Autowired private VehicleCustomerResolver vehicleCustomerResolver;
@Autowired private ServiceHistoryService serviceHistoryService;

public ServiceJobDTO updateJobStatus(Long jobId, JobStatus status) {
    ServiceJob updated = // ... existing logic

    if (status == JobStatus.COMPLETED || status == JobStatus.CLOSED) {
        // Create service history automatically
        ServiceHistoryCreateRequest request = new ServiceHistoryCreateRequest();
        request.setJobId(updated.getId());
        request.setVehicleId(updated.getVehicleId());
        request.setServiceDate(LocalDate.now());
        request.setOdometer(updated.getCurrentMileage().intValue());
        request.setSummary("Job completed: " + updated.getComplaintDescription());
        request.setTechnicianId(updated.getAssignedEmployeeId());
        request.setTotalCost(calculateJobTotal(updated.getId()));
        request.setNextServiceDate(calculateNextServiceDate(updated.getVehicleId()));

        // Member 06 will audit the CREATE internally
        serviceHistoryService.recordServiceHistory(request);
    }
    return // ...
}
```

### 4.4 Member 04 — Inventory

**Required Audit Events from Member 04:**

| Event | Action | EntityType | Description |
|-------|--------|------------|-------------|
| Stock movement | STOCK_IN/OUT/ADJUSTMENT | STOCK_ITEM | Quantity changes |
| GRN created | CREATE | GRN | Goods receipt note |
| GRN confirmed | CONFIRM | GRN | Stock received |
| Stock adjustment | ADJUSTMENT | STOCK_ITEM | Manual correction |

### 4.5 Member 05 — Billing, Payments, Ledger

**Required Audit Events from Member 05:**

| Event | Action | EntityType | Description |
|-------|--------|------------|-------------|
| Invoice created | CREATE | INVOICE | New invoice issued |
| Invoice status change | STATUS_CHANGE | INVOICE | DRAFT/SENT/PAID/OVERDUE/CANCELLED |
| Payment received | PAYMENT_RECEIVED | PAYMENT | Customer payment |
| Payment refunded | REFUND | PAYMENT | Refund processed |
| Ledger entry | LEDGER_ENTRY | CASH_LEDGER | Cash flow entry |

---

## 5. Transaction Boundaries

| Operation | Transaction Scope |
|-----------|-------------------|
| REST controller operations | `@Transactional` on service method (includes audit) |
| Scheduled workers | `@Transactional` on worker method (includes audit) |
| Background delivery | `@Transactional` on delivery method (includes audit) |

**Rule:** Audit event is written in the **same transaction** as the business operation. If business operation rolls back, audit rolls back.

---

## 6. Error Handling

| Failure Mode | Behavior |
|--------------|----------|
| Audit service unavailable | Business operation **fails** (fail-closed) |
| Invalid actor ID | `IllegalArgumentException` — operation fails |
| Invalid entity ID | `IllegalArgumentException` — operation fails |
| IP address > 50 chars | `IllegalArgumentException` — operation fails |

**Rationale:** Audit is a compliance requirement; inability to audit = inability to proceed.

---

## 7. Sensitive Data Exclusions

**Never log:**

| Data | Reason |
|------|--------|
| Passwords | Credential theft |
| JWT tokens | Session hijacking |
| API keys | Provider compromise |
| Full phone numbers | Privacy (use masked: `+94****7`) |
| Full email addresses | Privacy (use masked: `us****@domain.com`) |
| Credit card numbers | PCI DSS |
| PINs/OTPs | Account takeover |

**Masking utilities available in Member 06:**
```java
CustomerPhoneResolverImpl.maskPhone("+94771234567") // "+94****7"
NotificationService.maskRecipient("user@example.com", EMAIL) // "us****@example.com"
```

---

## 8. Idempotency & Duplicate Prevention

| Mechanism | Implementation |
|-----------|----------------|
| Service History | Unique constraint on `job_id` (DB) |
| Service Reminders | Unique index on `service_history_id` (DB) |
| Notifications | Idempotency key `notif-{id}-retry-{count}` passed to provider |
| Audit Logs | No deduplication — every operation logs |

---

## 9. Example Integration Code

### For Module Owners (Members 01–05)

```java
@Service
@RequiredArgsConstructor
public class YourModuleService {

    private final AuditLogService auditLogService;

    public void yourBusinessOperation(YourEntity entity, Long actorId, HttpServletRequest request) {
        // 1. Capture old state
        String oldValue = entitySnapshot(entity);

        // 2. Perform business logic
        entity.doSomething();
        YourEntity saved = repository.save(entity);

        // 3. Capture new state
        String newValue = entitySnapshot(saved);

        // 4. Audit in same transaction
        String ip = request != null ? request.getRemoteAddr() : null;
        auditLogService.logEvent(
            actorId,                          // Trusted actor
            "UPDATE",                         // Action
            "YOUR_ENTITY",                    // Entity type
            saved.getId(),                    // Entity ID
            oldValue,                         // Old state
            newValue,                         // New state
            "Description of what changed",    // Summary
            ip                                // Client IP
        );
    }

    private String entitySnapshot(YourEntity e) {
        return "field1=" + e.getField1() + ", field2=" + e.getField2();
    }
}
```

---

## 10. Responsibilities Summary

| Responsibility | Member 06 | Member 01 | Member 02 | Member 03 | Member 04 | Member 05 |
|----------------|-----------|-----------|-----------|-----------|-----------|-----------|
| AuditLogService implementation | ✅ Owns | — | — | — | — | — |
| AuditLog entity/migration | ✅ Owns | — | — | — | — | — |
| Calling `logEvent()` for own ops | ✅ Done | ⏳ Pending | ⏳ Pending | ⏳ Pending | ⏳ Pending | ⏳ Pending |
| Trusted actor resolution | ✅ Pattern | ✅ Uses JWT | ✅ Uses JWT | ✅ Uses JWT | ✅ Uses JWT | ✅ Uses JWT |
| System actor (0L) for background | ✅ Done | — | — | ⏳ Pending | — | — |
| Sensitive data masking | ✅ Utils | — | — | — | — | — |
| Audit queries API | ✅ Owns | — | — | — | — | — |

---

## 11. Open Dependencies (Blockers)

| Dependency | Required By | Status |
|------------|-------------|--------|
| Member 02: `smsConsent`, `mobileVerified` on Customer | Member 06 phone resolver | ❌ Blocked |
| Member 03: Call `serviceHistoryService.recordServiceHistory()` after job completion | Member 06 auto history creation (Task 6.1) | ❌ Blocked |
| Member 05: Payment/Ledger entities for cash flow | Member 06 actual cash flow reports (Task 6.10) | ❌ Blocked |

---

## 12. Testing

Member 06 provides audit integration tests:
- `AuditLogServiceTest` — 39 tests covering validation, queries, mapping
- `ServiceHistoryServiceTest` — tests CREATE/UPDATE/DELETE audit
- `ServiceReminderServiceTest` — tests reminder lifecycle audit
- `NotificationProviderTest` / `NotificationDeliveryWorkerTest` — tests notification audit

Run all Member 06 tests:
```bash
./mvnw test -Dtest=ServiceHistoryServiceTest,ServiceHistoryControllerTest,ServiceReminderServiceTest,ServiceReminderSchedulerTest,AuditLogServiceTest,ReportServiceImplTest,NotificationProviderTest,SmsNotificationProviderTest,NotificationDeliveryWorkerTest
```

---

**Document Version:** 1.0  
**Author:** Member 06  
**Status:** Member 06 implementation complete; cross-member integration pending.