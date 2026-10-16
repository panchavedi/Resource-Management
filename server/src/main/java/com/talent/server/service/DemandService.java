package com.talent.server.service;

import com.talent.server.dto.request.CreateDemandRequest;
import com.talent.server.dto.request.CreateDemandRequest.SkillRequirementRequest;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.entity.Allocation;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.entity.Skill;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.AllocationRepository;
import com.talent.server.repository.DemandRepository;
import com.talent.server.repository.SkillRepository;
import com.talent.server.repository.Specifications.Demands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.talent.server.repository.Specifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DemandService {

    private static final Logger log = LoggerFactory.getLogger(DemandService.class);

    private final DemandRepository demandRepository;
    private final SkillRepository skillRepository;
    private final AllocationRepository allocationRepository;
    private final Clock clock;

    public DemandService(DemandRepository demandRepository, SkillRepository skillRepository,
            AllocationRepository allocationRepository, Clock clock) {
        this.demandRepository = demandRepository;
        this.skillRepository = skillRepository;
        this.allocationRepository = allocationRepository;
        this.clock = clock;
    }

    public record DemandFilter(Collection<DemandStatus> statuses, DemandPriority priority, Long skillId, String query,
            LocalDate from, LocalDate to) {

        Specification<ProjectDemand> toSpecification() {
            return Specifications.allOf(Demands.statusIn(statuses), Demands.priority(priority),
                    Demands.requiresSkill(skillId), Demands.text(query), Demands.overlaps(from, to));
        }
    }

    @Transactional
    public DemandResponse create(CreateDemandRequest request, String actor) {
        List<SkillRequirementRequest> requirements = request.requiredSkills() == null ? List.of()
                : request.requiredSkills();
        Map<Long, Skill> skills = loadSkills(requirements);

        ProjectDemand demand = new ProjectDemand(request.projectName().trim(), request.role().trim(),
                blankToNull(request.description()), request.startDate(), request.endDate(),
                blankToNull(request.location()), Boolean.TRUE.equals(request.remoteAllowed()),
                request.allocationPercent(), request.headcount() == null ? 1 : request.headcount(),
                request.priority(), actor);
        for (SkillRequirementRequest req : requirements) {
            demand.addRequiredSkill(skills.get(req.skillId()), req.minimumProficiency(),
                    req.mandatory() == null || req.mandatory());
        }
        ProjectDemand saved = demandRepository.save(demand);
        log.info("Demand {} created by {} ({} / {})", saved.getId(), actor, saved.getProjectName(), saved.getRole());
        return DemandResponse.of(saved, 0, 0);
    }

    public PageResponse<DemandResponse> search(DemandFilter filter, Pageable pageable) {
        Page<ProjectDemand> page = demandRepository.findAll(filter.toSpecification(), pageable);
        Map<Long, long[]> counts = staffingCounts(page.getContent().stream().map(ProjectDemand::getId).toList());
        return PageResponse.of(page, d -> {
            long[] c = counts.getOrDefault(d.getId(), new long[2]);
            return DemandResponse.of(d, c[0], c[1]);
        });
    }

    public DemandResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public ProjectDemand getEntity(Long id) {
        return demandRepository.findWithSkillsById(id).orElseThrow(() -> ApiException.notFound("Demand", id));
    }

    public DemandResponse toResponse(ProjectDemand demand) {
        long[] c = staffingCounts(List.of(demand.getId())).getOrDefault(demand.getId(), new long[2]);
        return DemandResponse.of(demand, c[0], c[1]);
    }

    /**
     * Cancels the demand and every allocation still holding capacity for it,
     * releasing that capacity.
     * Allocation history is retained.
     */
    @Transactional
    public DemandResponse cancel(Long id, String actor, String reason) {
        ProjectDemand demand = demandRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Demand", id));
        if (demand.getStatus() == DemandStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Demand %d is already cancelled".formatted(id));
        }
        Instant now = clock.instant();
        String note = reason == null || reason.isBlank() ? "Demand cancelled" : "Demand cancelled: " + reason.trim();
        List<Allocation> active = allocationRepository.findByDemandIdAndStatusIn(id, AllocationStatus.CAPACITY_HOLDING);
        active.forEach(a -> a.cancel(actor, note, now));
        demand.cancel();
        log.info("Demand {} cancelled by {}; {} allocation(s) released", id, actor, active.size());
        return toResponse(demand);
    }

    /** demandId → [approved, pending]. One grouped query for the whole page. */
    private Map<Long, long[]> staffingCounts(Collection<Long> demandIds) {
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> counts = new HashMap<>();
        allocationRepository.countByDemandAndStatus(demandIds, AllocationStatus.CAPACITY_HOLDING).forEach(row -> {
            long[] c = counts.computeIfAbsent(row.getDemandId(), k -> new long[2]);
            c[row.getStatus() == AllocationStatus.APPROVED ? 0 : 1] = row.getTotal();
        });
        return counts;
    }

    private Map<Long, Skill> loadSkills(List<SkillRequirementRequest> requirements) {
        Set<Long> seen = new HashSet<>();
        for (SkillRequirementRequest r : requirements) {
            if (!seen.add(r.skillId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Skill %d is listed more than once in requiredSkills".formatted(r.skillId()));
            }
        }
        Map<Long, Skill> skills = skillRepository.findAllById(seen).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        seen.stream().filter(id -> !skills.containsKey(id)).findFirst().ifPresent(missing -> {
            throw ApiException.notFound("Skill", missing);
        });
        return skills;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
