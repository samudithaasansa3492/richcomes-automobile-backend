
package com.richcomes.garage.appointment.repository;

import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    List<Appointment> findByStatus(
            AppointmentStatus status
    );

    List<Appointment> findByRequestedDate(
            LocalDate requestedDate
    );

    List<Appointment> findByCustomerMobile(
            String customerMobile
    );

    List<Appointment> findByVehicleNumber(
            String vehicleNumber
    );

    List<Appointment> findByCustomer_Id(
            Long customerId
    );

    List<Appointment> findByVehicle_Id(
            Long vehicleId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

}
