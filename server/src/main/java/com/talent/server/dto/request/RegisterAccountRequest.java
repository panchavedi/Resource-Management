package com.talent.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,40}$", message = "Use 3–40 letters, numbers, dots, underscores or hyphens") String username,
        @NotBlank @Size(min = 10, max = 200) String password) {
    @Override public String toString() { return "RegisterAccountRequest[username=" + username + ", password=***]"; }
}
