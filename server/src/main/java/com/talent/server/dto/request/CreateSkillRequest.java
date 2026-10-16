package com.talent.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSkillRequest(
                @NotBlank @Size(max = 80) String name,
                @NotBlank @Size(max = 60) String category,
                @Size(max = 500) String description) {
}
