package com.talent.server.dto.response;

import com.talent.server.entity.Skill;

public record SkillResponse(Long id, String name, String category, String description) {

    public static SkillResponse of(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getDescription());
    }
}
