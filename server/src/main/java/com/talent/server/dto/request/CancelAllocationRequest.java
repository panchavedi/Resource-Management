package com.talent.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAllocationRequest(@NotBlank @Size(max = 500) String reason) {
}
