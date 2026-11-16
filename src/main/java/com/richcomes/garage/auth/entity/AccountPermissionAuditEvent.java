package com.richcomes.garage.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable event for one individual CASHIER permission grant/revoke. */
@Entity
@Table(name = "account_permission_audit", schema = "garage")
public class AccountPermissionAuditEvent {
    @Id private UUID id;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(name = "target_account_id", nullable = false) private Long targetAccountId;
    @Column(name = "permission_code", nullable = false, length = 100) private String permissionCode;
    @Column(name = "granted", nullable = false) private boolean granted;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected AccountPermissionAuditEvent() {}
    public AccountPermissionAuditEvent(Long actorId, Long targetAccountId,
                                       String permissionCode, boolean granted, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.actorId = Objects.requireNonNull(actorId);
        this.targetAccountId = Objects.requireNonNull(targetAccountId);
        this.permissionCode = Objects.requireNonNull(permissionCode);
        this.granted = granted;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
    public UUID getId() { return id; }
    public Long getActorId() { return actorId; }
    public Long getTargetAccountId() { return targetAccountId; }
    public String getPermissionCode() { return permissionCode; }
    public boolean isGranted() { return granted; }
    public Instant getOccurredAt() { return occurredAt; }
}
