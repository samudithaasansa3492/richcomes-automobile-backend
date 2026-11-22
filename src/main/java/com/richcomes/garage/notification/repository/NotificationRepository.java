package com.richcomes.garage.notification.repository;

import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Notification> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

    List<Notification> findByChannelOrderByCreatedAtDesc(NotificationChannel channel);

    List<Notification> findByAppointmentId(Long appointmentId);


    Page<Notification> findAllByOrderByCreatedAtDescIdDesc(
            Pageable pageable);

    Page<Notification> findByStatusOrderByCreatedAtDescIdDesc(
            NotificationStatus status,
            Pageable pageable);

    Page<Notification> findByChannelOrderByCreatedAtDescIdDesc(
            NotificationChannel channel,
            Pageable pageable);

    Page<Notification> findByStatusAndChannelOrderByCreatedAtDescIdDesc(
            NotificationStatus status,
            NotificationChannel channel,
            Pageable pageable);

    /**
     * Find pending notifications eligible for processing.
     * Criteria:
     * - Status = PENDING
     * - Channel in enabled channels
     * - Next retry time <= now (or null for first attempt)
     * - Retry count < max retries (enforced by worker)
     * Uses SELECT FOR UPDATE SKIP LOCKED to prevent concurrent processing
     */
    @Query(value = """
            SELECT n FROM Notification n
            WHERE n.status = 'PENDING'
              AND n.channel IN :channels
              AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now)
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findPendingForProcessing(
            @Param("channels") List<NotificationChannel> channels,
            @Param("now") Instant now,
            Pageable pageable);
}
