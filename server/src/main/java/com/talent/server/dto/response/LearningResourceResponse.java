package com.talent.server.dto.response;

import com.talent.server.entity.LearningResource;
import com.talent.server.enums.LearningFormat;

public record LearningResourceResponse(Long id, Long skillId, String skillName, String title, String provider,
        LearningFormat format, int targetLevel, int durationHours, String url) {

    public static LearningResourceResponse of(LearningResource lr) {
        return new LearningResourceResponse(lr.getId(), lr.getSkill().getId(), lr.getSkill().getName(), lr.getTitle(),
                lr.getProvider(), lr.getFormat(), lr.getTargetLevel(), lr.getDurationHours(), lr.getUrl());
    }
}
