package com.richcomes.garage.auth.repository;

import com.richcomes.garage.auth.entity.AccountPermissionAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only use in the service; no remote audit deletion endpoint. */
public interface AccountPermissionAuditRepository
        extends JpaRepository<AccountPermissionAuditEvent, UUID> {}
