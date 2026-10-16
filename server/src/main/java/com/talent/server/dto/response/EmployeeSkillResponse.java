package com.talent.server.dto.response;

import com.talent.server.entity.EmployeeSkill;
import com.talent.server.enums.EvidenceSource;
import com.talent.server.enums.EvidenceStatus;

import java.time.LocalDate;

public record EmployeeSkillResponse(
        Long id,
        Long skillId,
        String skillName,
        String category,
        int proficiencyLevel,
        String evidence,
        EvidenceSource evidenceSource,
        LocalDate lastValidatedAt,
        String validatedBy,
        EvidenceStatus evidenceStatus) {

    public static EmployeeSkillResponse of(EmployeeSkill es, EvidenceStatus status) {
        return new EmployeeSkillResponse(es.getId(), es.getSkill().getId(), es.getSkill().getName(),
                es.getSkill().getCategory(), es.getProficiencyLevel(), es.getEvidence(), es.getEvidenceSource(),
                es.getLastValidatedAt(), es.getValidatedBy(), status);
    }
}
