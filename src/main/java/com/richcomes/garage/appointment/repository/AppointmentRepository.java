
package com.richcomes.garage.appointment.repository;

import com.richcomes.garage.appointment.entity.Appointment;
import com.richcomes.garage.appointment.enums.AppointmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

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

}
