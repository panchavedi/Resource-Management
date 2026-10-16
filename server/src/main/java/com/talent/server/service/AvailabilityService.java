package com.talent.server.service;

import com.talent.server.config.AllocationProperties;
import com.talent.server.entity.Allocation;
import com.talent.server.entity.Employee;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.repository.AllocationRepository;
import com.talent.server.service.matching.AvailabilityCalculator;
import com.talent.server.service.matching.AvailabilityProfile;
import com.talent.server.service.matching.Booking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads capacity-holding allocations and delegates the arithmetic to
 * {@link AvailabilityCalculator}.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final AllocationRepository allocationRepository;
    private final AllocationProperties properties;

    public AvailabilityService(AllocationRepository allocationRepository, AllocationProperties properties) {
        this.allocationRepository = allocationRepository;
        this.properties = properties;
    }

    /** Employee capacity capped by the organisation-wide limit. */
    public int effectiveCapacity(Employee employee) {
        return Math.min(employee.getCapacityPercent(), properties.maxCapacityPercent());
    }

    public AvailabilityProfile forEmployee(Employee employee, LocalDate start, LocalDate end, int requestedPercent) {
        return forEmployee(employee, start, end, requestedPercent, null);
    }

    /**
     * @param excludeAllocationId allocation to ignore (used when re-validating a
     *                            pending allocation, which
     *                            already holds its own capacity)
     */
    public AvailabilityProfile forEmployee(Employee employee, LocalDate start, LocalDate end, int requestedPercent,
            Long excludeAllocationId) {
        List<Booking> bookings = allocationRepository
                .findOverlapping(employee.getId(), start, end, AllocationStatus.CAPACITY_HOLDING).stream()
                .filter(a -> !Objects.equals(a.getId(), excludeAllocationId))
                .map(Booking::of)
                .toList();
        return AvailabilityCalculator.calculate(start, end, effectiveCapacity(employee), requestedPercent, bookings);
    }

    /** Batch variant: one query for all employees. */
    public Map<Long, AvailabilityProfile> forEmployees(Collection<Employee> employees, LocalDate start, LocalDate end,
            int requestedPercent) {
        if (employees.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Booking>> bookingsByEmployee = allocationRepository
                .findOverlappingForEmployees(employees.stream().map(Employee::getId).toList(), start, end,
                        AllocationStatus.CAPACITY_HOLDING)
                .stream()
                .collect(Collectors.groupingBy((Allocation a) -> a.getEmployee().getId(),
                        Collectors.mapping(Booking::of, Collectors.toList())));
        Map<Long, AvailabilityProfile> result = new HashMap<>();
        for (Employee employee : employees) {
            result.put(employee.getId(), AvailabilityCalculator.calculate(start, end, effectiveCapacity(employee),
                    requestedPercent, bookingsByEmployee.getOrDefault(employee.getId(), List.of())));
        }
        return result;
    }
}
