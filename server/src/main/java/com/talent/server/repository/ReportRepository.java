package com.talent.server.repository;

import com.talent.server.dto.response.ReportResponses.AvailableResourceRow;
import com.talent.server.dto.response.ReportResponses.DepartmentUtilizationRow;
import com.talent.server.dto.response.ReportResponses.EmployeeUtilizationRow;
import com.talent.server.dto.response.ReportResponses.OpenDemandRow;
import com.talent.server.dto.response.ReportResponses.SkillGapCategoryRow;
import com.talent.server.dto.response.ReportResponses.SkillGapSkillRow;
import com.talent.server.dto.response.ReportResponses.SkillProficiencyRow;
import com.talent.server.dto.response.ReportResponses.UnstaffableDemandRow;
import com.talent.server.dto.response.ReportResponses.UpcomingDemandRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Executes the reporting SQL in {@code classpath:sql/reports}. The same files are the documented
 * answers to the SQL tasks, so what is documented is exactly what runs.
 */
@Repository
public class ReportRepository {

    private final JdbcClient jdbc;
    private final String openDemands = load("01_open_demands_by_role_priority.sql");
    private final String skillProficiency = load("02_resources_by_skill_proficiency.sql");
    private final String availableResources = load("03_available_resources.sql");
    private final String utilizationByEmployee = load("04a_utilization_by_employee.sql");
    private final String utilizationByDepartment = load("04b_utilization_by_department.sql");
    private final String unstaffableDemands = load("05_demands_without_eligible_candidate.sql");
    private final String skillGapsByCategory = load("06a_skill_gaps_by_category.sql");
    private final String skillGapsBySkill = load("06b_skill_gaps_by_skill.sql");

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<OpenDemandRow> openDemandsByRoleAndPriority() {
        return jdbc.sql(openDemands).query(OpenDemandRow.class).list();
    }

    public List<SkillProficiencyRow> resourcesBySkillAndProficiency() {
        return jdbc.sql(skillProficiency).query(SkillProficiencyRow.class).list();
    }

    public List<AvailableResourceRow> availableResources(LocalDate from, LocalDate to, int requestedPercent,
                                                         int maxCapacity) {
        return jdbc.sql(availableResources)
                .param("from", from).param("to", to).param("requested", requestedPercent)
                .param("maxCapacity", maxCapacity)
                .query(AvailableResourceRow.class).list();
    }

    public List<EmployeeUtilizationRow> utilizationByEmployee(LocalDate from, LocalDate to) {
        return jdbc.sql(utilizationByEmployee).param("from", from).param("to", to)
                .query(EmployeeUtilizationRow.class).list();
    }

    public List<DepartmentUtilizationRow> utilizationByDepartment(LocalDate from, LocalDate to) {
        return jdbc.sql(utilizationByDepartment).param("from", from).param("to", to)
                .query(DepartmentUtilizationRow.class).list();
    }

    public List<UnstaffableDemandRow> demandsWithoutEligibleCandidate(int maxCapacity) {
        return jdbc.sql(unstaffableDemands).param("maxCapacity", maxCapacity)
                .query(UnstaffableDemandRow.class).list();
    }

    public List<SkillGapCategoryRow> skillGapsByCategory() {
        return jdbc.sql(skillGapsByCategory).query(SkillGapCategoryRow.class).list();
    }

    public List<SkillGapSkillRow> skillGapsBySkill() {
        return jdbc.sql(skillGapsBySkill).query(SkillGapSkillRow.class).list();
    }

    // ------------------------------------------------------------------ dashboard KPIs

    public long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    public long openPositions() {
        return count("""
                SELECT COALESCE(SUM(d.headcount - (SELECT COUNT(*) FROM allocations a
                                                   WHERE a.demand_id = d.id AND a.status = 'APPROVED')), 0)
                FROM project_demands d WHERE d.status IN ('OPEN', 'PARTIALLY_FILLED')
                """);
    }

    public BigDecimal averageUtilization(LocalDate from, LocalDate to) {
        List<EmployeeUtilizationRow> rows = utilizationByEmployee(from, to);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = rows.stream().map(EmployeeUtilizationRow::utilizationPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rows.size()), 1, java.math.RoundingMode.HALF_UP);
    }

    public List<UpcomingDemandRow> demandsStartingSoon(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT d.id AS demand_id, d.project_name, d.role, d.priority, d.start_date,
                               d.headcount - (SELECT COUNT(*) FROM allocations a
                                              WHERE a.demand_id = d.id AND a.status = 'APPROVED') AS open_positions
                        FROM project_demands d
                        WHERE d.status IN ('OPEN', 'PARTIALLY_FILLED') AND d.start_date BETWEEN :from AND :to
                        ORDER BY d.start_date, d.id
                        LIMIT 8
                        """)
                .param("from", from).param("to", to)
                .query(UpcomingDemandRow.class).list();
    }

    private static String load(String name) {
        try {
            return new ClassPathResource("sql/reports/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing report SQL " + name, e);
        }
    }
}
