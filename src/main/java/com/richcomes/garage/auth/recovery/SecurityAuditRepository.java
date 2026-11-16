package com.richcomes.garage.auth.recovery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, UUID> {}
