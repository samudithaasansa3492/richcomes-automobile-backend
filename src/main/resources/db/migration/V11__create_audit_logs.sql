
CREATE TABLE garage.audit_logs (
    id BIGINT primary key ,

    actor_user_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,

    old_value TEXT,
    new_value TEXT,
    summary TEXT NOT NULL,

    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50)
);

-- Index for searching by actor
CREATE INDEX idx_audit_logs_actor
    ON garage.audit_logs(actor_user_id);

-- Index for searching by entity
CREATE INDEX idx_audit_logs_entity
    ON garage.audit_logs(entity_type, entity_id);

-- Index for searching by timestamp
CREATE INDEX idx_audit_logs_timestamp
    ON garage.audit_logs(timestamp);
