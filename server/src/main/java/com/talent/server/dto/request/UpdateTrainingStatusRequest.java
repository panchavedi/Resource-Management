package com.talent.server.dto.request;

import com.talent.server.enums.TrainingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTrainingStatusRequest(@NotNull TrainingStatus status) {
}
