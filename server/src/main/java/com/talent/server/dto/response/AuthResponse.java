package com.talent.server.dto.response;

import com.talent.server.enums.Role;

import java.time.Instant;
import java.util.List;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserInfo user) {

    public record UserInfo(String username, String displayName, List<Role> roles) {
    }
}
