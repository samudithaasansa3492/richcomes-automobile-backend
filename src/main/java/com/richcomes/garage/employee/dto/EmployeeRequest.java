
package com.richcomes.garage.employee.dto;

import com.richcomes.garage.employee.entity.EmployeePosition;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record EmployeeRequest(

    @NotBlank(message = "Employee code is required")
    String employeeCode,

    @NotBlank(message = "NIC is required")
    String nic,

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Phone number is required")
    String phone,

    @NotNull(message = "Position is required")
    EmployeePosition position,

    @NotNull(message = "Payment rate is required")
    @DecimalMin(value = "0.0", message = "Payment rate cannot be negative")
    BigDecimal paymentRate

) {}
