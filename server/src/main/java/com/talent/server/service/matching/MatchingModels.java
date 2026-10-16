package com.talent.server.service.matching;

import com.talent.server.entity.DemandSkill;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceSource;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detached, immutable snapshots of the data the scorer needs. Decoupling the scoring rules from JPA
 * entities keeps them pure (trivially unit-testable) and safe to use outside a transaction.
 */
public final class MatchingModels {

    private MatchingModels() {
    }

    public record Requirement(Long skillId, String skillName, String category, int minimumLevel, boolean mandatory) {

        static Requirement of(DemandSkill ds) {
            return new Requirement(ds.getSkill().getId(), ds.getSkill().getName(), ds.getSkill().getCategory(),
                    ds.getMinimumProficiency(), ds.isMandatory());
        }
    }

    public record SkillRecord(Long skillId, String skillName, String category, int level, String evidence,
                              EvidenceSource evidenceSource, LocalDate lastValidatedAt) {

        public static SkillRecord of(EmployeeSkill es) {
            return new SkillRecord(es.getSkill().getId(), es.getSkill().getName(), es.getSkill().getCategory(),
                    es.getProficiencyLevel(), es.getEvidence(), es.getEvidenceSource(), es.getLastValidatedAt());
        }
    }

    public record DemandProfile(Long id, String projectName, String role, LocalDate startDate, LocalDate endDate,
                                String location, boolean remoteAllowed, int allocationPercent, int headcount,
                                DemandPriority priority, DemandStatus status, List<Requirement> requirements) {

        public static DemandProfile of(ProjectDemand d) {
            return new DemandProfile(d.getId(), d.getProjectName(), d.getRole(), d.getStartDate(), d.getEndDate(),
                    d.getLocation(), d.isRemoteAllowed(), d.getAllocationPercent(), d.getHeadcount(), d.getPriority(),
                    d.getStatus(), d.getRequiredSkills().stream().map(Requirement::of).toList());
        }
    }

    public record CandidateProfile(Long employeeId, String employeeCode, String name, String jobTitle,
                                   String department, String location, boolean remoteEligible,
                                   EmploymentStatus employmentStatus, int capacityPercent,
                                   Map<Long, SkillRecord> skillsById) {

        public static CandidateProfile of(Employee e, Collection<EmployeeSkill> skills) {
            Map<Long, SkillRecord> byId = skills.stream().map(SkillRecord::of)
                    .collect(Collectors.toUnmodifiableMap(SkillRecord::skillId, Function.identity()));
            return new CandidateProfile(e.getId(), e.getEmployeeCode(), e.getName(), e.getJobTitle(),
                    e.getDepartment(), e.getLocation(), e.isRemoteEligible(), e.getEmploymentStatus(),
                    e.getCapacityPercent(), byId);
        }
    }
}
