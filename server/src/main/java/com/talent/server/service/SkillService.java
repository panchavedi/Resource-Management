package com.talent.server.service;

import com.talent.server.dto.request.CreateSkillRequest;
import com.talent.server.dto.response.SkillResponse;
import com.talent.server.entity.Skill;
import com.talent.server.exception.ApiException;
import com.talent.server.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<SkillResponse> findAll() {
        return skillRepository.findAllByOrderByCategoryAscNameAsc().stream().map(SkillResponse::of).toList();
    }

    public List<String> categories() {
        return skillRepository.findDistinctCategories();
    }

    @Transactional
    public SkillResponse create(CreateSkillRequest request) {
        String name = request.name().trim();
        if (skillRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.duplicate("Skill '%s' already exists".formatted(name));
        }
        Skill skill = skillRepository.save(new Skill(name, request.category().trim(), request.description()));
        return SkillResponse.of(skill);
    }

    public Skill getEntity(Long id) {
        return skillRepository.findById(id).orElseThrow(() -> ApiException.notFound("Skill", id));
    }
}
