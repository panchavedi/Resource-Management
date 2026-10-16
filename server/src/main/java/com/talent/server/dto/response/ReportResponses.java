package com.talent.server.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Row types of the SQL reports (column names map 1:1 to the record components). */
public final class ReportResponses {

    private ReportResponses() {
    }

    /** SQL task 1 – open demands by role and priority. */
    public record OpenDemandRow(String role, String priority, long demandCount, long totalHeadcount,
                                long approvedCount, long openPositions, LocalDate earliestStart) {
    }

    /** SQL task 2 – resources grouped by skill and proficiency. */
    public record SkillProficiencyRow(String category, String skill, int proficiencyLevel, long employeeCount,
                                      long validatedCount, String employees) {
    }

    /** SQL task 3 – available resources for a date range. */
    public record AvailableResourceRow(Long employeeId, String employeeCode, String name, String department,
                                       String location, int capacityPercent, int peakLoadPercent, int minFreePercent,
                                       long availableDays, long totalDays) {
    }

    /** SQL task 4 – utilisation by employee. */
    public record EmployeeUtilizationRow(Long employeeId, String employeeCode, String name, String department,
                                         int capacityPercent, BigDecimal utilizationPercent,
                                         BigDecimal tentativePercent) {
    }

    /** SQL task 4 – utilisation by department. */
    public record DepartmentUtilizationRow(String department, long employeeCount, BigDecimal utilizationPercent,
                                           BigDecimal tentativePercent) {
    }

    public record UtilizationReport(LocalDate from, LocalDate to, List<EmployeeUtilizationRow> byEmployee,
                                    List<DepartmentUtilizationRow> byDepartment) {
    }

    /** SQL task 5 – open demands without a single eligible candidate. */
    public record UnstaffableDemandRow(Long demandId, String projectName, String role, String priority,
                                       LocalDate startDate, LocalDate endDate, long skillQualifiedCount,
                                       String reason) {
    }

    /** SQL task 6 – skill gaps by category. */
    public record SkillGapCategoryRow(String category, long requirementCount, long uncoveredRequirements,
                                      long peopleShort, long upskillCandidates, String skillsShort,
                                      long openTrainingRecommendations) {
    }

    /** SQL task 6 – detail per skill. */
    public record SkillGapSkillRow(String category, String skill, long openDemands, long demandedHeadcount,
                                   long qualifiedSupply, long nearMissSupply, long peopleShort) {
    }

    public record SkillGapReport(List<SkillGapCategoryRow> byCategory, List<SkillGapSkillRow> bySkill) {
    }

    public record DashboardSummary(long openDemands, long criticalOpenDemands, long openPositions,
                                   long pendingApprovals, long activeEmployees, long benchEmployees,
                                   BigDecimal averageUtilizationNext30Days, long unstaffableDemands,
                                   long openTrainingRecommendations, List<UpcomingDemandRow> startingSoon) {
    }

    public record UpcomingDemandRow(Long demandId, String projectName, String role, String priority,
                                    LocalDate startDate, long openPositions) {
    }
}
