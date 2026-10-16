package com.talent.server.security;

import com.talent.server.config.SecurityProperties;
import com.talent.server.dto.response.AuthResponse;
import com.talent.server.enums.Role;
import com.talent.server.repository.RegisteredAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Issues short-lived HS256 access tokens carrying the user's roles and display
 * name.
 */
@Service
public class TokenService {

    static final String ROLES_CLAIM = "roles";
    static final String NAME_CLAIM = "name";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final Map<String, String> displayNames;
    private final RegisteredAccountRepository registeredAccounts;

    public TokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock, RegisteredAccountRepository registeredAccounts) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.registeredAccounts = registeredAccounts;
        this.displayNames = properties.users().stream().collect(Collectors.toMap(
                SecurityProperties.UserAccount::username, SecurityProperties.UserAccount::displayName, (a, b) -> a));
    }

    public AuthResponse issue(Authentication authentication) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        List<Role> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Role.valueOf(a.substring("ROLE_".length())))
                .toList();
        String username = authentication.getName();
        String displayName = registeredAccounts.findByUsernameIgnoreCase(username).map(a -> a.getDisplayName())
                .orElseGet(() -> displayNames.getOrDefault(username, username));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, roles.stream().map(Role::name).toList())
                .claim(NAME_CLAIM, displayName)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, new AuthResponse.UserInfo(username, displayName, roles));
    }

    public static AuthResponse.UserInfo userInfo(Jwt jwt) {
        List<String> roleNames = jwt.getClaimAsStringList(ROLES_CLAIM);
        List<Role> roles = roleNames == null ? List.of() : roleNames.stream().map(Role::valueOf).toList();
        String name = jwt.getClaimAsString(NAME_CLAIM);
        return new AuthResponse.UserInfo(jwt.getSubject(), name == null ? jwt.getSubject() : name, roles);
    }
}
