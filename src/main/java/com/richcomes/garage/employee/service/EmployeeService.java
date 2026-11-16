package com.richcomes.garage.employee.service;

import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.employee.dto.EmployeeRequest;
import com.richcomes.garage.employee.dto.EmployeeResponse;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.repository.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    private final AdminAccessGuard adminAccess;

    public EmployeeService(EmployeeRepository employeeRepository,
                           AdminAccessGuard adminAccess) {
        this.adminAccess = adminAccess;
        this.employeeRepository = employeeRepository;
    }

    // 1. Register employee
    public EmployeeResponse createEmployee(EmployeeRequest request) {

        if (employeeRepository.existsByEmployeeCode(
                request.employeeCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee code already exists");
        }

        if (employeeRepository.existsByNic(request.nic())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "NIC already exists");
        }

        Employee employee = new Employee();

        updateFields(employee, request);

        return toResponse(employeeRepository.save(employee));
    }

    // 2. Get all employees
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAllEmployees() {

        return employeeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 3. Get employee by ID
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(UUID id) {

        return toResponse(findEmployee(id));
    }

    // 4. Update employee
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse updateEmployee(
            UUID id, EmployeeRequest request) {
        adminAccess.lockAdministration();
        adminAccess.rejectTechnicalEmployeeMutation(id);
        if (request.position() == com.richcomes.garage.employee.entity.EmployeePosition.TECHNICIAN) {
            adminAccess.rejectTechnicianWithAccount(id);
        }
        Employee employee = findEmployee(id);

        if (!employee.getEmployeeCode()
                .equals(request.employeeCode())
                && employeeRepository.existsByEmployeeCode(
                        request.employeeCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee code already exists");
        }

        if (!employee.getNic().equals(request.nic())
                && employeeRepository.existsByNic(request.nic())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "NIC already exists");
        }

        updateFields(employee, request);

        return toResponse(employeeRepository.save(employee));
    }

    // 5. Deactivate employee
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse deactivateEmployee(UUID id, Long actorId) {
        adminAccess.lockAdministration();
        adminAccess.protectEmployee(id, actorId);

        Employee employee = findEmployee(id);

        employee.setActive(false);

        return toResponse(employeeRepository.save(employee));
    }

    private Employee findEmployee(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"));
    }

    private void updateFields(
            Employee employee, EmployeeRequest request) {

        employee.setEmployeeCode(request.employeeCode());
        employee.setNic(request.nic());
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setPhone(request.phone());
        employee.setPosition(request.position());
        employee.setPaymentRate(request.paymentRate());
    }

    private EmployeeResponse toResponse(Employee employee) {

        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getPhone(),
                employee.getPosition(),
                employee.getPaymentRate(),
                employee.isActive()
        );
    }
}
