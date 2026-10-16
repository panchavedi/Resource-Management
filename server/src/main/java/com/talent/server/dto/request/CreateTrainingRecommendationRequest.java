package com.talent.server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTrainingRecommendationRequest(
                @NotNull Long employeeId,
                @NotNull Long skillId,
                Long demandId,
                Long learningResourceId,
                @NotNull @Min(1) @Max(5) Integer targetLevel,
                @NotBlank @Size(max = 500) String reason) {
}
