
package com.richcomes.garage.employee.repository;

import com.richcomes.garage.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository
        extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeCode(
            String employeeCode
    );

    boolean existsByNic(String nic);

    boolean existsByEmployeeCode(
            String employeeCode
    );
}
