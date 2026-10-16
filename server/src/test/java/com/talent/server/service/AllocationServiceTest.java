package com.talent.server.service;

import com.talent.server.config.AllocationProperties;
import com.talent.server.entity.Allocation;
import com.talent.server.entity.Employee;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.AllocationRepository;
import com.talent.server.repository.DemandRepository;
import com.talent.server.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Workflow guards of {@link AllocationService}, isolated from the database with
 * Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-01T09:00:00Z");
    private static final LocalDate START = LocalDate.of(2027, 2, 1);

    @Mock
    AllocationRepository allocations;
    @Mock
    DemandRepository demands;
    @Mock
    EmployeeRepository employees;
    @Mock
    CandidateMatchingService matching;

    private AllocationService service;
    private Allocation pending;

    @BeforeEach
    void setUp() {
        service = new AllocationService(allocations, demands, employees, matching, new AllocationProperties(100, true),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Employee employee = new Employee("E1", "Asha Rao", "asha@example.com", "Engineer", "Engineering", "Pune", true,
                EmploymentStatus.ACTIVE, 100);
        ProjectDemand demand = new ProjectDemand("Atlas", "Engineer", null, START, START.plusDays(30), "Pune", false,
                100, 1, DemandPriority.HIGH, "manager");
        pending = Allocation.request(employee, demand, START, START.plusDays(30), 100, "manager", null, null, NOW);

        AllocationRepository.LockKeys keys = new AllocationRepository.LockKeys() {
            public Long getDemandId() {
                return 1L;
            }

            public Long getEmployeeId() {
                return 2L;
            }
        };
        lenient().when(allocations.findLockKeys(7L)).thenReturn(Optional.of(keys));
        lenient().when(demands.findByIdForUpdate(1L)).thenReturn(Optional.of(demand));
        lenient().when(employees.findByIdForUpdate(2L)).thenReturn(Optional.of(employee));
        lenient().when(allocations.findWithEventsById(7L)).thenReturn(Optional.of(pending));
    }

    @Test
    @DisplayName("Four-eyes rule: the requester cannot approve their own allocation")
    void selfApprovalRejected() {
        assertThatThrownBy(() -> service.approve(7L, null, "manager"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        verify(matching, never()).evaluate(any(), any(), any(), any(), anyInt(), any());
        assertThat(pending.getStatus()).isEqualTo(AllocationStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("Locks are taken in a fixed order (demand, then employee) before the allocation is read")
    void locksTakenBeforeRead() {
        service.reject(7L, "Not needed", "approver");

        var order = org.mockito.Mockito.inOrder(allocations, demands, employees);
        order.verify(allocations).findLockKeys(7L);
        order.verify(demands).findByIdForUpdate(1L);
        order.verify(employees).findByIdForUpdate(2L);
        order.verify(allocations).findWithEventsById(7L);
        assertThat(pending.getStatus()).isEqualTo(AllocationStatus.REJECTED);
        assertThat(pending.getEvents()).hasSize(2);
    }

    @Test
    @DisplayName("Only pending allocations can be approved or rejected; cancelled ones cannot be cancelled again")
    void invalidTransitions() {
        pending.cancel("manager", "No longer needed", NOW);

        assertThatThrownBy(() -> service.approve(7L, null, "approver"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThatThrownBy(() -> service.reject(7L, null, "approver"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThatThrownBy(() -> service.cancel(7L, "again", "manager"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("Unknown allocation id yields RESOURCE_NOT_FOUND")
    void notFound() {
        when(allocations.findLockKeys(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L, null, "approver"))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
