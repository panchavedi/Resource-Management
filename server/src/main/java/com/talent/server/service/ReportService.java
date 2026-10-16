package com.talent.server.service;

import com.talent.server.config.AllocationProperties;
import com.talent.server.dto.response.ReportResponses.AvailableResourceRow;
import com.talent.server.dto.response.ReportResponses.DashboardSummary;
import com.talent.server.dto.response.ReportResponses.OpenDemandRow;
import com.talent.server.dto.response.ReportResponses.SkillGapReport;
import com.talent.server.dto.response.ReportResponses.SkillProficiencyRow;
import com.talent.server.dto.response.ReportResponses.UnstaffableDemandRow;
import com.talent.server.dto.response.ReportResponses.UtilizationReport;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Per-day reports expand the range into rows; cap it to keep them cheap. */
    private static final long MAX_RANGE_DAYS = 366;

    private final ReportRepository reports;
    private final AllocationProperties allocationProperties;
    private final Clock clock;

    public ReportService(ReportRepository reports, AllocationProperties allocationProperties, Clock clock) {
        this.reports = reports;
        this.allocationProperties = allocationProperties;
        this.clock = clock;
    }

    public List<OpenDemandRow> openDemands() {
        return reports.openDemandsByRoleAndPriority();
    }

    public List<SkillProficiencyRow> skillProficiency() {
        return reports.resourcesBySkillAndProficiency();
    }

    public List<AvailableResourceRow> availableResources(LocalDate from, LocalDate to, int requestedPercent) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(30);
        validateRange(start, end);
        return reports.availableResources(start, end, requestedPercent, allocationProperties.maxCapacityPercent());
    }

    public UtilizationReport utilization(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(89);
        validateRange(start, end);
        return new UtilizationReport(start, end, reports.utilizationByEmployee(start, end),
                reports.utilizationByDepartment(start, end));
    }

    public List<UnstaffableDemandRow> demandsWithoutEligibleCandidate() {
        return reports.demandsWithoutEligibleCandidate(allocationProperties.maxCapacityPercent());
    }

    public SkillGapReport skillGaps() {
        return new SkillGapReport(reports.skillGapsByCategory(), reports.skillGapsBySkill());
    }

    public DashboardSummary dashboard() {
        LocalDate today = LocalDate.now(clock);
        return new DashboardSummary(
                reports.count("SELECT COUNT(*) FROM project_demands WHERE status IN ('OPEN', 'PARTIALLY_FILLED')"),
                reports.count(
                        "SELECT COUNT(*) FROM project_demands WHERE status IN ('OPEN', 'PARTIALLY_FILLED') AND priority = 'CRITICAL'"),
                reports.openPositions(),
                reports.count("SELECT COUNT(*) FROM allocations WHERE status = 'PENDING_APPROVAL'"),
                reports.count("SELECT COUNT(*) FROM employees WHERE employment_status = 'ACTIVE'"),
                reports.count("SELECT COUNT(*) FROM employees WHERE employment_status = 'BENCH'"),
                reports.averageUtilization(today, today.plusDays(29)),
                reports.demandsWithoutEligibleCandidate(allocationProperties.maxCapacityPercent()).size(),
                reports.count(
                        "SELECT COUNT(*) FROM training_recommendations WHERE status IN ('RECOMMENDED', 'IN_PROGRESS')"),
                reports.demandsStartingSoon(today, today.plusDays(30)));
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE,
                    "Report range is limited to %d days".formatted(MAX_RANGE_DAYS));
        }
    }
}
