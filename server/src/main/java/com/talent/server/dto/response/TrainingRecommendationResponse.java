package com.talent.server.dto.response;

import com.talent.server.entity.LearningResource;
import com.talent.server.entity.TrainingRecommendation;
import com.talent.server.enums.RecommendationSource;
import com.talent.server.enums.TrainingStatus;

import java.time.Instant;

public record TrainingRecommendationResponse(
        Long id,
        Long employeeId,
        String employeeName,
        Long skillId,
        String skillName,
        String skillCategory,
        Long demandId,
        String projectName,
        Integer currentLevel,
        int targetLevel,
        String reason,
        LearningResourceResponse learningResource,
        RecommendationSource source,
        TrainingStatus status,
        String createdBy,
        Instant createdAt) {

    public static TrainingRecommendationResponse of(TrainingRecommendation t) {
        LearningResource lr = t.getLearningResource();
        return new TrainingRecommendationResponse(t.getId(), t.getEmployee().getId(), t.getEmployee().getName(),
                t.getSkill().getId(), t.getSkill().getName(), t.getSkill().getCategory(),
                t.getDemand() == null ? null : t.getDemand().getId(),
                t.getDemand() == null ? null : t.getDemand().getProjectName(),
                t.getCurrentLevel(), t.getTargetLevel(), t.getReason(),
                lr == null ? null : LearningResourceResponse.of(lr), t.getSource(), t.getStatus(), t.getCreatedBy(),
                t.getCreatedAt());
    }
}
