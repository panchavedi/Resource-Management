package com.talent.server.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.talent.server.enums.Role;
import com.talent.server.security.ProblemResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import com.talent.server.repository.ManagerAccessGrantRepository;
import com.talent.server.repository.RegisteredAccountRepository;
import com.talent.server.enums.AccountStatus;

/**
 * Stateless JWT security.
 * <ul>
 *   <li>{@code POST /api/auth/login} exchanges credentials for a signed HS256 token.</li>
 *   <li>Every other {@code /api/**} call requires {@code Authorization: Bearer <token>}.</li>
 *   <li>Roles from the token's {@code roles} claim drive {@code @PreAuthorize} rules on controllers.</li>
 * </ul>
 * CSRF protection is disabled because no cookies are used for authentication (bearer tokens only).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponseWriter problems) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems.entryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problems.entryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()));
        return http.build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET is not set: using a random signing key. Tokens become invalid on restart.");
            return new SecretKeySpec(random, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Demo directory from configuration; passwords arrive via environment variables and are hashed at startup. */
    @Bean
    UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder passwordEncoder,
            ManagerAccessGrantRepository managerGrants, RegisteredAccountRepository registeredAccounts) {
        var configured = properties.users().stream().filter(account -> account.password() != null && !account.password().isBlank())
                .collect(java.util.stream.Collectors.toMap(a -> a.username().toLowerCase(java.util.Locale.ROOT),
                        a -> User.withUsername(a.username()).password(passwordEncoder.encode(a.password())).roles(a.roles().stream().map(Role::name).toArray(String[]::new)).build()));
        if (configured.isEmpty()) log.warn("No users are configured. Set APP_*_PASSWORD environment variables (see .env.example).");
        return username -> {
            UserDetails base = configured.get(username.trim().toLowerCase(java.util.Locale.ROOT));
            if (base == null) {
                var account = registeredAccounts.findByUsernameIgnoreCase(username.trim())
                        .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                        .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Account not active"));
                return User.withUsername(account.getUsername()).password(account.getPasswordHash())
                        .roles(Role.RESOURCE_MANAGER.name()).build();
            }
            SecurityProperties.UserAccount account = properties.users().stream().filter(a -> a.username().equalsIgnoreCase(username.trim())).findFirst().orElseThrow();
            List<String> roles = new ArrayList<>(account.roles().stream().map(Role::name).toList());
            if (managerGrants.existsByUsernameIgnoreCase(account.username()) && !roles.contains(Role.RESOURCE_MANAGER.name())) roles.add(Role.RESOURCE_MANAGER.name());
            return User.withUsername(account.username()).password(base.getPassword()).roles(roles.toArray(String[]::new)).build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
