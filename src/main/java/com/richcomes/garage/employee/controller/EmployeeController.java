
package com.richcomes.garage.employee.controller;

import com.richcomes.garage.employee.dto.EmployeeRequest;
import com.richcomes.garage.employee.dto.EmployeeResponse;
import com.richcomes.garage.employee.service.EmployeeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // Create Employee
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(
            @Valid @RequestBody EmployeeRequest request) {

        return employeeService.createEmployee(request);
    }

    // Get All Employees
    @GetMapping
    public List<EmployeeResponse> getAllEmployees() {

        return employeeService.getAllEmployees();
    }

    // Get Employee By ID
    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(
            @PathVariable UUID id) {

        return employeeService.getEmployeeById(id);
    }

    // Update Employee
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeRequest request) {

        return employeeService.updateEmployee(id, request);
    }

    // Deactivate Employee
    @PatchMapping("/{id}/deactivate")
    public EmployeeResponse deactivateEmployee(
            @PathVariable UUID id) {

        return employeeService.deactivateEmployee(id);
    }
}
