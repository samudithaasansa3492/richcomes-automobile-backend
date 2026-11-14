package com.richcomes.garage.notification.repository;

import com.richcomes.garage.notification.entity.Notification;
import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;

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
}
