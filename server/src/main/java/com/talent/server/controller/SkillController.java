package com.talent.server.controller;

import com.talent.server.dto.request.CreateSkillRequest;
import com.talent.server.dto.response.SkillResponse;
import com.talent.server.security.Roles;
import com.talent.server.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @Operation(summary = "Skill taxonomy ordered by category and name")
    public List<SkillResponse> list() {
        return skillService.findAll();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return skillService.categories();
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Add a skill to the taxonomy")
    public ResponseEntity<SkillResponse> create(@Valid @RequestBody CreateSkillRequest request) {
        SkillResponse created = skillService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }
}
