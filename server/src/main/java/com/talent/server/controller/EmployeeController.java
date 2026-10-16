package com.talent.server.controller;

import com.talent.server.dto.request.CreateEmployeeRequest;
import com.talent.server.dto.request.UpdateEmploymentStatusRequest;
import com.talent.server.dto.request.UpsertEmployeeSkillRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.EmployeeResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.dto.response.SkillsMatrixResponse;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.security.Roles;
import com.talent.server.service.EmployeeService;
import com.talent.server.service.EmployeeService.EmployeeFilter;
import com.talent.server.service.matching.AvailabilityProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @Operation(summary = "Search employees (text, department, location, status, skill at minimum level)")
    public PageResponse<EmployeeResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) List<EmploymentStatus> status,
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minLevel,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.search(new EmployeeFilter(q, department, location, status, skillId, minLevel), pageable);
    }

    @GetMapping("/facets")
    @Operation(summary = "Distinct departments and locations for filters")
    public Map<String, List<String>> facets() {
        return employeeService.facets();
    }

    @GetMapping("/skills-matrix")
    @Operation(summary = "Employee × skill proficiency grid")
    public SkillsMatrixResponse skillsMatrix(@RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String category) {
        return employeeService.skillsMatrix(q, department, category);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Create an employee")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.MANAGE)
    public EmployeeResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateEmploymentStatusRequest request) {
        return employeeService.updateStatus(id, request.employmentStatus());
    }

    @PostMapping("/{id}/skills")
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Record or re-assess a skill (201 when created, 200 when updated)")
    public ResponseEntity<EmployeeSkillResponse> upsertSkill(@PathVariable Long id,
            @Valid @RequestBody UpsertEmployeeSkillRequest request,
            Authentication authentication) {
        var result = employeeService.upsertSkill(id, request, authentication.getName());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.skill());
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    @PreAuthorize(Roles.MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSkill(@PathVariable Long id, @PathVariable Long skillId) {
        employeeService.removeSkill(id, skillId);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Day-accurate availability for a date range and percentage")
    public AvailabilityProfile availability(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int allocationPercent) {
        return employeeService.availability(id, startDate, endDate, allocationPercent);
    }

    @GetMapping("/{id}/allocations")
    public List<AllocationResponse> allocations(@PathVariable Long id) {
        return employeeService.allocations(id);
    }
}
