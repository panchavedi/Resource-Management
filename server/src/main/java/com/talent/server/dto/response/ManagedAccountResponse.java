package com.talent.server.dto.response;

import com.talent.server.enums.Role;
import java.util.List;

public record ManagedAccountResponse(String username, String displayName, List<Role> roles, boolean managerAccess, boolean managerAccessFixed) {}
