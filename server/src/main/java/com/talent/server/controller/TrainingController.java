package com.talent.server.controller;

import com.talent.server.dto.request.CreateTrainingRecommendationRequest;
import com.talent.server.dto.request.UpdateTrainingStatusRequest;
import com.talent.server.dto.response.TrainingRecommendationResponse;
import com.talent.server.enums.TrainingStatus;
import com.talent.server.security.Roles;
import com.talent.server.service.TrainingRecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/training-recommendations")
@Tag(name = "Training recommendations")
public class TrainingController {

    private final TrainingRecommendationService trainingService;

    public TrainingController(TrainingRecommendationService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping
    public List<TrainingRecommendationResponse> search(@RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) List<TrainingStatus> status,
            @RequestParam(required = false) String category) {
        return trainingService.search(employeeId, status, category);
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingRecommendationResponse create(@Valid @RequestBody CreateTrainingRecommendationRequest request,
            Authentication authentication) {
        return trainingService.create(request, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.MANAGE)
    public TrainingRecommendationResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateTrainingStatusRequest request) {
        return trainingService.updateStatus(id, request.status());
    }
}
