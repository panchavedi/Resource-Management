package com.talent.server.service;

import com.talent.server.dto.response.AvailableResourceResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.Specifications.Employees;
import com.talent.server.service.matching.AvailabilityProfile;
import com.talent.server.service.matching.EvidenceClassifier;
import com.talent.server.repository.Specifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Who is free, with which skills, for this window?" — assignable employees
 * ranked by availability
 * coverage, then by average stored level across the requested skills.
 */
@Service
@Transactional(readOnly = true)
public class ResourceSearchService {

        private static final int MAX_RESULTS = 200;

        private final EmployeeRepository employeeRepository;
        private final AvailabilityService availabilityService;
        private final EvidenceClassifier evidenceClassifier;

        public ResourceSearchService(EmployeeRepository employeeRepository, AvailabilityService availabilityService,
                        EvidenceClassifier evidenceClassifier) {
                this.employeeRepository = employeeRepository;
                this.availabilityService = availabilityService;
                this.evidenceClassifier = evidenceClassifier;
        }

        /**
         * @param skillIds           every listed skill must be held at
         *                           {@code minProficiency} or above
         * @param requestedPercent   percentage the caller wants to allocate (drives
         *                           FULL / PARTIAL)
         * @param includeUnavailable include employees with no free capacity (status
         *                           NONE)
         */
        public record SearchCriteria(LocalDate startDate, LocalDate endDate, Collection<Long> skillIds,
                        Integer minProficiency, String location, String department, int requestedPercent,
                        boolean includeUnavailable) {
        }

        public List<AvailableResourceResponse> search(SearchCriteria criteria) {
                if (criteria.endDate().isBefore(criteria.startDate())) {
                        throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "endDate must be on or after startDate");
                }
                Set<Long> skillIds = criteria.skillIds() == null ? Set.of() : Set.copyOf(criteria.skillIds());
                List<Specification<Employee>> specs = new ArrayList<>(List.of(
                                Employees.statusIn(List.of(EmploymentStatus.ACTIVE, EmploymentStatus.BENCH))));
                specs.add(Employees.location(criteria.location()));
                specs.add(Employees.department(criteria.department()));
                skillIds.forEach(id -> specs.add(Employees.hasSkill(id, criteria.minProficiency())));
                List<Employee> employees = employeeRepository.findAll(Specifications.allOf(specs));

                Map<Long, AvailabilityProfile> availability = availabilityService.forEmployees(employees,
                                criteria.startDate(), criteria.endDate(), criteria.requestedPercent());

                return employees.stream()
                                .map(e -> toResponse(e, availability.get(e.getId()), skillIds))
                                .filter(r -> criteria.includeUnavailable()
                                                || r.availability().status() != AvailabilityStatus.NONE)
                                .sorted(Comparator.comparingDouble(
                                                (AvailableResourceResponse r) -> r.availability().coveragePercent())
                                                .reversed()
                                                .thenComparing(Comparator.comparingDouble(
                                                                (AvailableResourceResponse r) -> r
                                                                                .matchedSkillAverage() == null ? 0
                                                                                                : r.matchedSkillAverage())
                                                                .reversed())
                                                .thenComparing(AvailableResourceResponse::name))
                                .limit(MAX_RESULTS)
                                .toList();
        }

        private AvailableResourceResponse toResponse(Employee e, AvailabilityProfile availability, Set<Long> skillIds) {
                List<EmployeeSkill> matched = e.getSkills().stream()
                                .filter(s -> skillIds.contains(s.getSkill().getId()))
                                .toList();
                Double average = matched.isEmpty() ? null
                                : Math.round(matched.stream().mapToInt(EmployeeSkill::getProficiencyLevel).average()
                                                .orElse(0) * 10) / 10.0;
                return new AvailableResourceResponse(e.getId(), e.getEmployeeCode(), e.getName(), e.getJobTitle(),
                                e.getDepartment(), e.getLocation(), e.isRemoteEligible(), e.getEmploymentStatus(),
                                availabilityService.effectiveCapacity(e), availability,
                                matched.stream().map(s -> EmployeeSkillResponse.of(s,
                                                evidenceClassifier.classify(s.getLastValidatedAt())))
                                                .toList(),
                                average);
        }
}
