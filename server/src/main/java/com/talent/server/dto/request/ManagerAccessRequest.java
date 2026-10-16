package com.talent.server.dto.request;

import jakarta.validation.constraints.NotNull;

public record ManagerAccessRequest(@NotNull Boolean enabled) {}
