
package com.richcomes.garage.employee.dto;

import com.richcomes.garage.employee.entity.EmployeePosition;

import java.math.BigDecimal;
import java.util.UUID;

public record EmployeeResponse(

    UUID id,
    String employeeCode,
    String firstName,
    String lastName,
    String phone,
    EmployeePosition position,
    BigDecimal paymentRate,
    boolean active

) {}
