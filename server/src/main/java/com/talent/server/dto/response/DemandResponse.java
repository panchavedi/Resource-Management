package com.talent.server.dto.response;

import com.talent.server.entity.DemandSkill;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * @param approvedCount allocations approved against the headcount
 * @param pendingCount  allocations awaiting approval
 */
public record DemandResponse(
                Long id,
                String projectName,
                String role,
                String description,
                LocalDate startDate,
                LocalDate endDate,
                long durationDays,
                String location,
                boolean remoteAllowed,
                int allocationPercent,
                int headcount,
                DemandPriority priority,
                DemandStatus status,
                long approvedCount,
                long pendingCount,
                List<RequiredSkill> requiredSkills,
                String createdBy,
                Instant createdAt,
                Instant updatedAt) {

        public record RequiredSkill(Long skillId, String skillName, String category, int minimumProficiency,
                        boolean mandatory) {

                static RequiredSkill of(DemandSkill ds) {
                        return new RequiredSkill(ds.getSkill().getId(), ds.getSkill().getName(),
                                        ds.getSkill().getCategory(),
                                        ds.getMinimumProficiency(), ds.isMandatory());
                }
        }

        public static DemandResponse of(ProjectDemand d, long approvedCount, long pendingCount) {
                List<RequiredSkill> skills = d.getRequiredSkills().stream()
                                .map(RequiredSkill::of)
                                .sorted(Comparator.comparing(RequiredSkill::mandatory).reversed()
                                                .thenComparing(RequiredSkill::skillName))
                                .toList();
                return new DemandResponse(d.getId(), d.getProjectName(), d.getRole(), d.getDescription(),
                                d.getStartDate(),
                                d.getEndDate(), d.durationDays(), d.getLocation(), d.isRemoteAllowed(),
                                d.getAllocationPercent(),
                                d.getHeadcount(), d.getPriority(), d.getStatus(), approvedCount, pendingCount, skills,
                                d.getCreatedBy(), d.getCreatedAt(), d.getUpdatedAt());
        }
}
