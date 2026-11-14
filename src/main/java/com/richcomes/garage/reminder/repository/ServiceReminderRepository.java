package com.richcomes.garage.reminder.repository;

import com.richcomes.garage.reminder.entity.ServiceReminder;
import com.richcomes.garage.reminder.enums.ServiceReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ServiceReminderRepository extends JpaRepository<ServiceReminder, Long> {

    List<ServiceReminder> findByVehicleIdOrderByNextServiceDateAsc(Long vehicleId);

    List<ServiceReminder> findByCustomerIdOrderByReminderDateAsc(Long customerId);

    List<ServiceReminder> findByStatusOrderByReminderDateAsc(ServiceReminderStatus status);

    List<ServiceReminder> findByStatusAndNotificationIdIsNullAndReminderDateLessThanEqual(
            ServiceReminderStatus status, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ServiceReminder r where r.id = :id")
    Optional<ServiceReminder> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    List<ServiceReminder> findByServiceHistoryId(Long serviceHistoryId);
    boolean existsByServiceHistoryIdAndStatus(
        Long serviceHistoryId,
        ServiceReminderStatus status
    );
}
