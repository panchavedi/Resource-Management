package com.talent.server.service;

import com.talent.server.config.AllocationProperties;
import com.talent.server.dto.request.CreateAllocationRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.entity.Allocation;
import com.talent.server.entity.Employee;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.AllocationRepository;
import com.talent.server.repository.DemandRepository;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.Specifications.Allocations;
import com.talent.server.service.matching.AvailabilityProfile;
import com.talent.server.service.matching.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import com.talent.server.repository.Specifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Allocation workflow: propose → approve | reject, and cancel.
 *
 * <h2>Concurrency</h2>
 * Every capacity-changing operation locks rows in a fixed order — demand, then
 * employee
 * ({@code SELECT … FOR UPDATE}) — and only then re-reads allocations and
 * re-checks every rule.
 * Requests touching the same employee are therefore serialised, and the later
 * one sees the earlier
 * one's committed allocation; the fixed order prevents deadlocks.
 * {@code @Version} on allocations
 * turns stale approve/cancel attempts into 409 responses, and a partial unique
 * index rejects a second
 * active allocation for the same employee and demand at the database level.
 *
 * <h2>Idempotency</h2>
 * A client-supplied {@code Idempotency-Key} is stored with the allocation.
 * Replaying the same key
 * returns the original allocation instead of creating another one.
 */
@Service
@Transactional(readOnly = true)
public class AllocationService {

        private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

        private final AllocationRepository allocationRepository;
        private final DemandRepository demandRepository;
        private final EmployeeRepository employeeRepository;
        private final CandidateMatchingService matchingService;
        private final AllocationProperties properties;
        private final Clock clock;

        public AllocationService(AllocationRepository allocationRepository, DemandRepository demandRepository,
                        EmployeeRepository employeeRepository, CandidateMatchingService matchingService,
                        AllocationProperties properties, Clock clock) {
                this.allocationRepository = allocationRepository;
                this.demandRepository = demandRepository;
                this.employeeRepository = employeeRepository;
                this.matchingService = matchingService;
                this.properties = properties;
                this.clock = clock;
        }

        /**
         * Result of a proposal; {@code replayed} is true when an idempotency key
         * matched an earlier request.
         */
        public record ProposalResult(AllocationResponse allocation, boolean replayed) {
        }

        @Transactional(timeout = 15)
        public ProposalResult propose(CreateAllocationRequest request, String idempotencyKey, String actor) {
                ProjectDemand demand = lockDemand(request.demandId());
                Employee employee = lockEmployee(request.employeeId());

                if (idempotencyKey != null) {
                        var previous = allocationRepository.findByIdempotencyKey(idempotencyKey);
                        if (previous.isPresent()) {
                                Allocation existing = previous.get();
                                if (!existing.getEmployee().getId().equals(employee.getId())
                                                || !existing.getDemand().getId().equals(demand.getId())) {
                                        throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                                                        "Idempotency-Key was already used for a different allocation request");
                                }
                                return new ProposalResult(AllocationResponse.detailed(existing), true);
                        }
                }

                LocalDate start = request.startDate() != null ? request.startDate() : demand.getStartDate();
                LocalDate end = request.endDate() != null ? request.endDate() : demand.getEndDate();
                int percent = request.allocationPercent() != null ? request.allocationPercent()
                                : demand.getAllocationPercent();

                if (end.isBefore(start) || !demand.covers(start, end)) {
                        throw new ApiException(ErrorCode.INVALID_DATE_RANGE,
                                        "Allocation period must lie within the demand period %s to %s".formatted(
                                                        demand.getStartDate(), demand.getEndDate()),
                                        Map.of("demandStartDate", demand.getStartDate().toString(),
                                                        "demandEndDate", demand.getEndDate().toString()));
                }
                requireDemandOpen(demand);
                if (allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(employee.getId(), demand.getId(),
                                AllocationStatus.CAPACITY_HOLDING)) {
                        throw new ApiException(ErrorCode.ALLOCATION_ALREADY_EXISTS,
                                        "%s already has an active allocation for this demand"
                                                        .formatted(employee.getName()));
                }
                long holding = allocationRepository.countByDemandIdAndStatusIn(demand.getId(),
                                AllocationStatus.CAPACITY_HOLDING);
                if (holding >= demand.getHeadcount()) {
                        throw new ApiException(ErrorCode.HEADCOUNT_FILLED,
                                        "Demand headcount of %d is already covered by pending or approved allocations"
                                                        .formatted(demand.getHeadcount()),
                                        Map.of("headcount", demand.getHeadcount(), "activeAllocations", holding));
                }
                assertEligibleWithCapacity(demand, employee, start, end, percent, null);

                Allocation allocation = Allocation.request(employee, demand, start, end, percent, actor,
                                blankToNull(request.note()), idempotencyKey, clock.instant());
                allocationRepository.saveAndFlush(allocation);
                log.info("Allocation {} proposed by {}: employee {} → demand {} ({}% {}..{})", allocation.getId(),
                                actor,
                                employee.getEmployeeCode(), demand.getId(), percent, start, end);
                return new ProposalResult(AllocationResponse.detailed(allocation), false);
        }

        /**
         * Confirms a pending allocation. Every rule is re-validated at commit time
         * under lock: the demand may
         * have been filled, the employee's skills or status may have changed, or other
         * allocations may have
         * consumed the capacity since the proposal.
         */
        @Transactional(timeout = 15)
        public AllocationResponse approve(Long allocationId, String note, String approver) {
                Allocation allocation = loadForUpdate(allocationId);
                ProjectDemand demand = allocation.getDemand();
                Employee employee = allocation.getEmployee();

                if (allocation.getStatus() != AllocationStatus.PENDING_APPROVAL) {
                        throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                                        "Only PENDING_APPROVAL allocations can be approved (current: %s)"
                                                        .formatted(allocation.getStatus()));
                }
                if (properties.fourEyesApproval() && allocation.getRequestedBy().equalsIgnoreCase(approver)) {
                        throw new ApiException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                                        "Four-eyes rule: the requester (%s) cannot approve their own allocation"
                                                        .formatted(approver));
                }
                requireDemandOpen(demand);
                long approved = allocationRepository.countByDemandIdAndStatus(demand.getId(),
                                AllocationStatus.APPROVED);
                if (approved >= demand.getHeadcount()) {
                        throw new ApiException(ErrorCode.HEADCOUNT_FILLED,
                                        "Demand headcount of %d is already filled".formatted(demand.getHeadcount()));
                }
                assertEligibleWithCapacity(demand, employee, allocation.getStartDate(), allocation.getEndDate(),
                                allocation.getAllocationPercent(), allocation.getId());

                allocation.approve(approver, blankToNull(note), clock.instant());
                demand.refreshStaffingStatus(approved + 1);
                allocationRepository.flush();
                log.info("Allocation {} approved by {}", allocationId, approver);
                return AllocationResponse.detailed(allocation);
        }

        @Transactional(timeout = 15)
        public AllocationResponse reject(Long allocationId, String note, String approver) {
                Allocation allocation = loadForUpdate(allocationId);
                allocation.reject(approver, blankToNull(note), clock.instant());
                allocationRepository.flush();
                log.info("Allocation {} rejected by {}", allocationId, approver);
                return AllocationResponse.detailed(allocation);
        }

        /**
         * Cancels a pending or approved allocation. Capacity is released; the row and
         * its history remain.
         */
        @Transactional(timeout = 15)
        public AllocationResponse cancel(Long allocationId, String reason, String actor) {
                Allocation allocation = loadForUpdate(allocationId);
                boolean wasApproved = allocation.getStatus() == AllocationStatus.APPROVED;
                allocation.cancel(actor, reason.trim(), clock.instant());
                allocationRepository.flush();
                if (wasApproved) {
                        ProjectDemand demand = allocation.getDemand();
                        demand.refreshStaffingStatus(
                                        allocationRepository.countByDemandIdAndStatus(demand.getId(),
                                                        AllocationStatus.APPROVED));
                }
                log.info("Allocation {} cancelled by {}: {}", allocationId, actor, reason);
                return AllocationResponse.detailed(allocation);
        }

        public AllocationResponse get(Long id) {
                return AllocationResponse.detailed(allocationRepository.findWithEventsById(id)
                                .orElseThrow(() -> ApiException.notFound("Allocation", id)));
        }

        public PageResponse<AllocationResponse> search(Collection<AllocationStatus> statuses, Long employeeId,
                        Long demandId, Pageable pageable) {
                Specification<Allocation> spec = Specifications.allOf(Allocations.statusIn(statuses),
                                Allocations.employee(employeeId), Allocations.demand(demandId));
                return PageResponse.of(allocationRepository.findAll(spec, pageable), AllocationResponse::summary);
        }

        // ------------------------------------------------------------------ rules

        private void assertEligibleWithCapacity(ProjectDemand demand, Employee employee, LocalDate start, LocalDate end,
                        int percent, Long excludeAllocationId) {
                CandidateResponse evaluation = matchingService.evaluate(demand, employee, start, end, percent,
                                excludeAllocationId);
                if (!employee.isAssignable() || !evaluation.meetsMandatorySkills()) {
                        List<String> reasons = evaluation.exclusionReasons().stream()
                                        .filter(r -> !r.startsWith("Not available"))
                                        .toList();
                        throw new ApiException(ErrorCode.CANDIDATE_NOT_ELIGIBLE,
                                        "%s is not eligible for this demand: %s".formatted(employee.getName(),
                                                        String.join("; ", reasons)),
                                        Map.of("reasons", reasons));
                }
                AvailabilityProfile availability = evaluation.availability();
                if (availability.status() != AvailabilityStatus.FULL) {
                        Map<String, Object> details = new LinkedHashMap<>();
                        details.put("capacityPercent", availability.capacityPercent());
                        details.put("peakLoadPercent", availability.peakLoadPercent());
                        details.put("requestedPercent", percent);
                        details.put("minFreePercent", availability.minFreePercent());
                        details.put("conflictingAllocationIds", availability.bookings().stream()
                                        .map(Booking::allocationId).filter(Objects::nonNull).toList());
                        throw new ApiException(ErrorCode.CAPACITY_EXCEEDED,
                                        "Allocating %d%% would exceed %s's capacity of %d%%: existing load peaks at %d%% between %s and %s"
                                                        .formatted(percent, employee.getName(),
                                                                        availability.capacityPercent(),
                                                                        availability.peakLoadPercent(), start, end),
                                        details);
                }
        }

        private static void requireDemandOpen(ProjectDemand demand) {
                if (!demand.getStatus().acceptsAllocations()) {
                        throw new ApiException(ErrorCode.DEMAND_NOT_OPEN,
                                        "Demand %d is %s and does not accept allocations".formatted(demand.getId(),
                                                        demand.getStatus()));
                }
        }

        // ------------------------------------------------------------------ locking

        /**
         * Takes the demand and employee locks (in that order) and then loads the
         * allocation with its history.
         */
        private Allocation loadForUpdate(Long allocationId) {
                AllocationRepository.LockKeys keys = allocationRepository.findLockKeys(allocationId)
                                .orElseThrow(() -> ApiException.notFound("Allocation", allocationId));
                lockDemand(keys.getDemandId());
                lockEmployee(keys.getEmployeeId());
                return allocationRepository.findWithEventsById(allocationId)
                                .orElseThrow(() -> ApiException.notFound("Allocation", allocationId));
        }

        private ProjectDemand lockDemand(Long demandId) {
                return demandRepository.findByIdForUpdate(demandId)
                                .orElseThrow(() -> ApiException.notFound("Demand", demandId));
        }

        private Employee lockEmployee(Long employeeId) {
                return employeeRepository.findByIdForUpdate(employeeId)
                                .orElseThrow(() -> ApiException.notFound("Employee", employeeId));
        }

        private static String blankToNull(String value) {
                return value == null || value.isBlank() ? null : value.trim();
        }
}
