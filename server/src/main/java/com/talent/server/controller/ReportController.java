package com.talent.server.controller;

import com.talent.server.dto.response.ReportResponses.AvailableResourceRow;
import com.talent.server.dto.response.ReportResponses.DashboardSummary;
import com.talent.server.dto.response.ReportResponses.OpenDemandRow;
import com.talent.server.dto.response.ReportResponses.SkillGapReport;
import com.talent.server.dto.response.ReportResponses.SkillProficiencyRow;
import com.talent.server.dto.response.ReportResponses.UnstaffableDemandRow;
import com.talent.server.dto.response.ReportResponses.UtilizationReport;
import com.talent.server.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** SQL reports (see {@code src/main/resources/sql/reports}). */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Headline KPIs for the dashboard")
    public DashboardSummary dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/utilization")
    @Operation(summary = "Utilisation by employee and department (defaults: today + 90 days)")
    public UtilizationReport utilization(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.utilization(from, to);
    }

    @GetMapping("/open-demands")
    @Operation(summary = "Open demands by role and priority")
    public List<OpenDemandRow> openDemands() {
        return reportService.openDemands();
    }

    @GetMapping("/skill-proficiency")
    @Operation(summary = "Resources grouped by skill and proficiency")
    public List<SkillProficiencyRow> skillProficiency() {
        return reportService.skillProficiency();
    }

    @GetMapping("/available-resources")
    @Operation(summary = "Available resources for a date range")
    public List<AvailableResourceRow> availableResources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int allocationPercent) {
        return reportService.availableResources(from, to, allocationPercent);
    }

    @GetMapping("/unstaffable-demands")
    @Operation(summary = "Open demands with no eligible candidate")
    public List<UnstaffableDemandRow> unstaffableDemands() {
        return reportService.demandsWithoutEligibleCandidate();
    }

    @GetMapping("/skill-gaps")
    @Operation(summary = "Skill gaps grouped by category (plus per-skill detail)")
    public SkillGapReport skillGaps() {
        return reportService.skillGaps();
    }
}
