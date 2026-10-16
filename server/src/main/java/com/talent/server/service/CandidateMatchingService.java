package com.talent.server.service;

import com.talent.server.config.AllocationProperties;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.MatchResultResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.EmployeeSkillRepository;
import com.talent.server.service.matching.AvailabilityProfile;
import com.talent.server.service.matching.CandidateScorer;
import com.talent.server.service.matching.MatchingModels.CandidateProfile;
import com.talent.server.service.matching.MatchingModels.DemandProfile;
import com.talent.server.service.matching.MatchingModels.Requirement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the explainable candidate ranking for a demand. All rules live in
 * {@link CandidateScorer};
 * this service only gathers data in a bounded number of queries (pool, skills,
 * allocations).
 */
@Service
@Transactional(readOnly = true)
public class CandidateMatchingService {

        private final DemandService demandService;
        private final EmployeeRepository employeeRepository;
        private final EmployeeSkillRepository employeeSkillRepository;
        private final AvailabilityService availabilityService;
        private final CandidateScorer scorer;
        private final AllocationProperties allocationProperties;
        private final Clock clock;

        public CandidateMatchingService(DemandService demandService, EmployeeRepository employeeRepository,
                        EmployeeSkillRepository employeeSkillRepository,
                        AvailabilityService availabilityService, CandidateScorer scorer,
                        AllocationProperties allocationProperties, Clock clock) {
                this.demandService = demandService;
                this.employeeRepository = employeeRepository;
                this.employeeSkillRepository = employeeSkillRepository;
                this.availabilityService = availabilityService;
                this.scorer = scorer;
                this.allocationProperties = allocationProperties;
                this.clock = clock;
        }

        public MatchResultResponse rankCandidates(Long demandId) {
                ProjectDemand demand = demandService.getEntity(demandId);
                DemandProfile profile = DemandProfile.of(demand);

                List<Long> mandatorySkillIds = profile.requirements().stream()
                                .filter(Requirement::mandatory).map(Requirement::skillId).toList();
                List<Employee> pool = mandatorySkillIds.isEmpty()
                                ? employeeRepository.findAll()
                                : employeeRepository.findHoldingAnySkill(mandatorySkillIds);
                int notConsidered = (int) (employeeRepository.count() - pool.size());

                Map<Long, List<EmployeeSkill>> skillsByEmployee = pool.isEmpty() ? Map.of()
                                : employeeSkillRepository.findByEmployeeIds(pool.stream().map(Employee::getId).toList())
                                                .stream()
                                                .collect(Collectors.groupingBy(es -> es.getEmployee().getId()));
                Map<Long, AvailabilityProfile> availability = availabilityService.forEmployees(pool,
                                profile.startDate(), profile.endDate(), profile.allocationPercent());

                List<CandidateResponse> ranked = new ArrayList<>();
                List<CandidateResponse> excluded = new ArrayList<>();
                for (Employee employee : pool) {
                        CandidateProfile candidate = CandidateProfile.of(employee,
                                        skillsByEmployee.getOrDefault(employee.getId(), List.of()));
                        CandidateResponse evaluation = scorer.evaluate(profile, candidate,
                                        availability.get(employee.getId()));
                        (scorer.isRanked(evaluation) ? ranked : excluded).add(evaluation);
                }
                ranked.sort(CandidateScorer.RANKING);
                List<CandidateResponse> rankedWithPositions = new ArrayList<>(ranked.size());
                for (int i = 0; i < ranked.size(); i++) {
                        rankedWithPositions.add(ranked.get(i).withRank(i + 1));
                }
                excluded.sort(Comparator.comparingDouble(CandidateResponse::totalScore).reversed()
                                .thenComparing(CandidateResponse::employeeCode));

                return new MatchResultResponse(demandService.toResponse(demand), clock.instant(), criteria(),
                                pool.size(), Math.max(0, notConsidered), List.copyOf(rankedWithPositions),
                                List.copyOf(excluded));
        }

        /**
         * Evaluates one employee for a demand over a specific window and percentage.
         * Used to re-validate
         * eligibility when an allocation is proposed and again when it is approved.
         */
        public CandidateResponse evaluate(ProjectDemand demand, Employee employee, LocalDate start, LocalDate end,
                        int percent, Long excludeAllocationId) {
                DemandProfile profile = DemandProfile.of(demand);
                List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeIds(List.of(employee.getId()));
                AvailabilityProfile availability = availabilityService.forEmployee(employee, start, end, percent,
                                excludeAllocationId);
                return scorer.evaluate(profile, CandidateProfile.of(employee, skills), availability);
        }

        public MatchResultResponse.Criteria criteria() {
                var props = scorer.properties();
                return new MatchResultResponse.Criteria(props.mandatorySkillPolicy(), props.weights(),
                                props.evidenceFreshnessMonths(), allocationProperties.maxCapacityPercent(), List.of(
                                                "Employment status must be ACTIVE or BENCH",
                                                "Every mandatory skill must be stored at or above its minimum level (policy: "
                                                                + props.mandatorySkillPolicy() + ")",
                                                "Availability is computed per day from pending and approved allocations; NONE excludes, PARTIAL is flagged",
                                                "Effective capacity = min(employee capacity, organisation limit of "
                                                                + allocationProperties.maxCapacityPercent() + "%)",
                                                "Score = skill fit × %.2f + availability × %.2f + location × %.2f + evidence quality × %.2f"
                                                                .formatted(props.weights().skill(),
                                                                                props.weights().availability(),
                                                                                props.weights().location(),
                                                                                props.weights().evidence()),
                                                "Ties broken by skill fit, availability, then employee code"));
        }
}
