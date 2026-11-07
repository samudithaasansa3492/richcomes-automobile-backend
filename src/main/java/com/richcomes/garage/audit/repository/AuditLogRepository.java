package com.richcomes.garage.audit.repository;

import com.richcomes.garage.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, Long entityId);

    List<AuditLog> findByActorUserIdOrderByTimestampDesc(Long actorUserId);

    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
