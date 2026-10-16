package com.talent.server.dto.response;

import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Employee × skill grid. {@code cells} is keyed by skill id; an absent key
 * means "no stored record"
 * (shown as empty, never as level 0).
 */
public record SkillsMatrixResponse(List<SkillResponse> skills, List<Row> rows) {

    public record Row(Long employeeId, String employeeCode, String name, String jobTitle, String department,
            String location, EmploymentStatus employmentStatus, Map<Long, Cell> cells) {
    }

    public record Cell(int level, EvidenceStatus evidenceStatus, LocalDate lastValidatedAt) {
    }
}
