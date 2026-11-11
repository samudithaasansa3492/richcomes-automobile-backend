package com.richcomes.garage.auth;

import com.richcomes.garage.auth.exception.AccountOperationException;
import com.richcomes.garage.auth.service.AdminAccessGuard;
import com.richcomes.garage.employee.entity.Employee;
import com.richcomes.garage.employee.repository.EmployeeRepository;
import com.richcomes.garage.employee.service.EmployeeService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static com.richcomes.garage.auth.exception.AccountOperationException.Code.LAST_ACTIVE_ADMIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeAdminProtectionTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AdminAccessGuard guard = mock(AdminAccessGuard.class);
    private final EmployeeService service = new EmployeeService(employees, guard);

    @Test void lastAdministratorFailurePreventsEmployeeMutation() {
        var id = UUID.randomUUID();
        doThrow(new AccountOperationException(LAST_ACTIVE_ADMIN, "Last admin"))
                .when(guard).protectEmployee(id, 99L);
        assertThrows(AccountOperationException.class, () -> service.deactivateEmployee(id, 99L));
        var order = inOrder(guard);
        order.verify(guard).lockAdministration();
        order.verify(guard).protectEmployee(id, 99L);
        verifyNoInteractions(employees);
    }

    @Test void permittedDeactivationLocksBeforeLoadingEmployee() {
        var id = UUID.randomUUID();
        var employee = new Employee();
        when(employees.findById(id)).thenReturn(Optional.of(employee));
        when(employees.save(employee)).thenReturn(employee);
        assertFalse(service.deactivateEmployee(id, 99L).active());
        var order = inOrder(guard, employees);
        order.verify(guard).lockAdministration();
        order.verify(guard).protectEmployee(id, 99L);
        order.verify(employees).findById(id);
        order.verify(employees).save(employee);
    }
}
