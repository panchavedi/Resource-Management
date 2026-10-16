package com.talent.server.service;

import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.LearningResourceResponse;
import com.talent.server.entity.LearningResource;
import com.talent.server.repository.LearningResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds learning paths for skill gaps from the curated catalogue only. When
 * the catalogue has no
 * suitable resource the path says so explicitly rather than inventing a course.
 */
@Service
@Transactional(readOnly = true)
public class LearningPathService {

    private final LearningResourceRepository learningResourceRepository;

    public LearningPathService(LearningResourceRepository learningResourceRepository) {
        this.learningResourceRepository = learningResourceRepository;
    }

    /**
     * One employee's gap in one skill. {@code currentLevel == null} means no stored
     * record.
     */
    public record SkillGapRef(Long employeeId, String employeeName, Long skillId, String skillName, String category,
            boolean mandatory, Integer currentLevel, int requiredLevel) {
    }

    /**
     * @param steps       catalogue resources ordered by target level, covering
     *                    current → required level
     * @param catalogNote set when the catalogue has no suitable resource for the
     *                    gap
     */
    public record LearningPath(Long employeeId, String employeeName, Long skillId, String skillName, String category,
            boolean mandatory, Integer currentLevel, int targetLevel,
            List<LearningResourceResponse> steps, int totalHours, String catalogNote) {
    }

    public static List<SkillGapRef> gapsOf(Collection<CandidateResponse> candidates) {
        return candidates.stream()
                .flatMap(c -> c.gaps().stream().map(g -> new SkillGapRef(c.employeeId(), c.name(), g.skillId(),
                        g.skillName(), g.category(), g.mandatory(), g.actualLevel(), g.requiredLevel())))
                .toList();
    }

    public List<LearningPath> pathsFor(List<SkillGapRef> gaps) {
        if (gaps.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = gaps.stream().map(SkillGapRef::skillId).distinct().toList();
        Map<Long, List<LearningResource>> catalogue = learningResourceRepository.findBySkillIds(skillIds).stream()
                .collect(Collectors.groupingBy(lr -> lr.getSkill().getId()));
        return gaps.stream().map(gap -> path(gap, catalogue.getOrDefault(gap.skillId(), List.of()))).toList();
    }

    private static LearningPath path(SkillGapRef gap, List<LearningResource> resources) {
        int current = gap.currentLevel() == null ? 0 : gap.currentLevel();
        List<LearningResource> steps = resources.stream()
                .filter(r -> r.getTargetLevel() > current && r.getTargetLevel() <= gap.requiredLevel())
                .toList();
        if (steps.isEmpty()) {
            // Nothing lands exactly in range: take the smallest resource that reaches the
            // required level.
            steps = resources.stream().filter(r -> r.getTargetLevel() >= gap.requiredLevel()).limit(1).toList();
        }
        String note = resources.isEmpty()
                ? "No catalogue resource exists for %s; a learning owner should add one".formatted(gap.skillName())
                : steps.isEmpty()
                        ? "Catalogue has no resource reaching level %d for %s".formatted(gap.requiredLevel(),
                                gap.skillName())
                        : null;
        return new LearningPath(gap.employeeId(), gap.employeeName(), gap.skillId(), gap.skillName(), gap.category(),
                gap.mandatory(), gap.currentLevel(), gap.requiredLevel(),
                steps.stream().map(LearningResourceResponse::of).toList(),
                steps.stream().mapToInt(LearningResource::getDurationHours).sum(), note);
    }
}
