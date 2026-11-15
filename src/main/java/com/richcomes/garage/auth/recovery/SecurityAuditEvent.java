package com.richcomes.garage.auth.recovery;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Fixed event codes and identifiers only; never credentials, email, tokens, or request bodies. */
@Entity
@Table(name = "security_audit_events", schema = "garage")
public class SecurityAuditEvent {
    @Id UUID id;
    @Column(name = "actor_id") Long actorId;
    @Column(name = "target_id", nullable = false) Long targetId;
    @Column(name = "event_code", nullable = false, length = 64) String eventCode;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    protected SecurityAuditEvent() {}
    SecurityAuditEvent(Long actor, Long target, String event, Instant time) {
        id = UUID.randomUUID(); actorId = actor; targetId = target; eventCode = event; occurredAt = time;
    }
}
