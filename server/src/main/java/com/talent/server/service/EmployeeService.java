package com.talent.server.service;

import com.talent.server.dto.request.CreateEmployeeRequest;
import com.talent.server.dto.request.UpsertEmployeeSkillRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.EmployeeResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.dto.response.SkillResponse;
import com.talent.server.dto.response.SkillsMatrixResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.entity.Skill;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.AllocationRepository;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.SkillRepository;
import com.talent.server.repository.Specifications.Employees;
import com.talent.server.service.matching.AvailabilityProfile;
import com.talent.server.service.matching.EvidenceClassifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.talent.server.repository.Specifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final SkillRepository skillRepository;
    private final AllocationRepository allocationRepository;
    private final AvailabilityService availabilityService;
    private final EvidenceClassifier evidenceClassifier;

    public EmployeeService(EmployeeRepository employeeRepository, SkillRepository skillRepository,
            AllocationRepository allocationRepository, AvailabilityService availabilityService,
            EvidenceClassifier evidenceClassifier) {
        this.employeeRepository = employeeRepository;
        this.skillRepository = skillRepository;
        this.allocationRepository = allocationRepository;
        this.availabilityService = availabilityService;
        this.evidenceClassifier = evidenceClassifier;
    }

    public record EmployeeFilter(String query, String department, String location,
            Collection<EmploymentStatus> statuses, Long skillId, Integer minLevel) {

        Specification<Employee> toSpecification() {
            return Specifications.allOf(Employees.text(query), Employees.department(department),
                    Employees.location(location), Employees.statusIn(statuses), Employees.hasSkill(skillId, minLevel));
        }
    }

    public PageResponse<EmployeeResponse> search(EmployeeFilter filter, Pageable pageable) {
        return PageResponse.of(employeeRepository.findAll(filter.toSpecification(), pageable), this::toResponse);
    }

    public EmployeeResponse get(Long id) {
        return toResponse(
                employeeRepository.findWithSkillsById(id).orElseThrow(() -> ApiException.notFound("Employee", id)));
    }

    public Employee getEntity(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employee", id));
    }

    public Map<String, List<String>> facets() {
        return Map.of("departments", employeeRepository.findDistinctDepartments(),
                "locations", employeeRepository.findDistinctLocations());
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        String code = request.employeeCode().trim().toUpperCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw ApiException.duplicate("Employee code %s is already in use".formatted(code));
        }
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.duplicate("Email %s is already in use".formatted(email));
        }
        Employee employee = new Employee(code, request.name().trim(), email, trimToNull(request.jobTitle()),
                request.department().trim(), trimToNull(request.location()),
                Boolean.TRUE.equals(request.remoteEligible()), request.employmentStatus(),
                request.capacityPercent() == null ? 100 : request.capacityPercent());
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse updateStatus(Long id, EmploymentStatus status) {
        Employee employee = getEntity(id);
        employee.changeStatus(status);
        return toResponse(employee);
    }

    /**
     * Creates or re-assesses a skill record. Returns the record and whether it was
     * newly created.
     */
    @Transactional
    public SkillUpsertResult upsertSkill(Long employeeId, UpsertEmployeeSkillRequest request, String actor) {
        Employee employee = employeeRepository.findWithSkillsById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee", employeeId));
        Skill skill = skillRepository.findById(request.skillId())
                .orElseThrow(() -> ApiException.notFound("Skill", request.skillId()));
        var existing = employee.findSkill(skill.getId());
        EmployeeSkill record;
        if (existing.isPresent()) {
            record = existing.get();
            record.update(request.proficiencyLevel(), trimToNull(request.evidence()), request.evidenceSource(),
                    request.lastValidatedAt(), actor);
        } else {
            record = employee.addSkill(new EmployeeSkill(employee, skill, request.proficiencyLevel(),
                    trimToNull(request.evidence()), request.evidenceSource(), request.lastValidatedAt(), actor));
        }
        employeeRepository.flush();
        return new SkillUpsertResult(toSkillResponse(record), existing.isEmpty());
    }

    public record SkillUpsertResult(EmployeeSkillResponse skill, boolean created) {
    }

    @Transactional
    public void removeSkill(Long employeeId, Long skillId) {
        Employee employee = employeeRepository.findWithSkillsById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee", employeeId));
        if (!employee.removeSkill(skillId)) {
            throw ApiException.notFound("Skill record", skillId);
        }
    }

    public AvailabilityProfile availability(Long employeeId, LocalDate start, LocalDate end, int requestedPercent) {
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "endDate must be on or after startDate");
        }
        return availabilityService.forEmployee(getEntity(employeeId), start, end, requestedPercent);
    }

    public List<AllocationResponse> allocations(Long employeeId) {
        getEntity(employeeId);
        return allocationRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .map(AllocationResponse::summary).toList();
    }

    /**
     * Employee × skill grid, optionally narrowed by department, skill category or
     * text.
     */
    public SkillsMatrixResponse skillsMatrix(String query, String department, String category) {
        List<Skill> skills = skillRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(s -> category == null || category.isBlank() || s.getCategory().equalsIgnoreCase(category))
                .toList();
        List<Employee> employees = employeeRepository.findAll(
                Specifications.allOf(Employees.text(query), Employees.department(department)),
                Sort.by("department", "name"));
        List<SkillsMatrixResponse.Row> rows = employees.stream().map(e -> {
            Map<Long, SkillsMatrixResponse.Cell> cells = new LinkedHashMap<>();
            e.getSkills().stream()
                    .sorted(Comparator.comparing(es -> es.getSkill().getName()))
                    .forEach(es -> cells.put(es.getSkill().getId(), new SkillsMatrixResponse.Cell(
                            es.getProficiencyLevel(), evidenceClassifier.classify(es.getLastValidatedAt()),
                            es.getLastValidatedAt())));
            return new SkillsMatrixResponse.Row(e.getId(), e.getEmployeeCode(), e.getName(), e.getJobTitle(),
                    e.getDepartment(), e.getLocation(), e.getEmploymentStatus(), cells);
        }).toList();
        return new SkillsMatrixResponse(skills.stream().map(SkillResponse::of).toList(), rows);
    }

    EmployeeResponse toResponse(Employee e) {
        List<EmployeeSkillResponse> skills = e.getSkills().stream()
                .map(this::toSkillResponse)
                .sorted(Comparator.comparing(EmployeeSkillResponse::proficiencyLevel).reversed()
                        .thenComparing(EmployeeSkillResponse::skillName))
                .toList();
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getName(), e.getEmail(), e.getJobTitle(),
                e.getDepartment(), e.getLocation(), e.isRemoteEligible(), e.getEmploymentStatus(),
                e.getCapacityPercent(), skills, e.getCreatedAt(), e.getUpdatedAt());
    }

    EmployeeSkillResponse toSkillResponse(EmployeeSkill es) {
        return EmployeeSkillResponse.of(es, evidenceClassifier.classify(es.getLastValidatedAt()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
