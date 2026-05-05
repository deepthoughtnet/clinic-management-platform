package com.deepthoughtnet.clinic.api.config;

import com.deepthoughtnet.clinic.platform.spring.security.TenantRoleAuthorityFilter;
import com.deepthoughtnet.clinic.platform.spring.web.RequestContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String primaryIssuer;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestContextFilter clinicRequestContextFilter,
            TenantRoleAuthorityFilter clinicTenantRoleAuthorityFilter
    ) throws Exception {
        http.cors(cors -> {});
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/graphiql", "/graphiql/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.POST, "/graphql").authenticated()
                .anyRequest().authenticated()
        );

        http.oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
        http.httpBasic(Customizer.withDefaults());

        http.addFilterAfter(
                clinicRequestContextFilter,
                org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class
        );
        http.addFilterAfter(clinicTenantRoleAuthorityFilter, RequestContextFilter.class);

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(primaryIssuer).build();
        Set<String> allowedIssuers = buildAllowedIssuers(primaryIssuer);

        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuerPresenceValidator = jwt -> {
            String iss = jwt.getClaimAsString(JwtClaimNames.ISS);
            if (iss == null || iss.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN,
                        "Missing iss claim",
                        null
                ));
            }
            return OAuth2TokenValidatorResult.success();
        };
        OAuth2TokenValidator<Jwt> multiIssuerValidator = jwt -> {
            String iss = jwt.getClaimAsString(JwtClaimNames.ISS);
            if (iss == null || iss.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN,
                        "Missing iss claim",
                        null
                ));
            }
            String normalized = normalize(iss);
            if (allowedIssuers.contains(normalized)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "Invalid issuer: " + normalized + " (allowed: " + allowedIssuers + ")",
                    null
            ));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerPresenceValidator,
                multiIssuerValidator
        ));
        return decoder;
    }

    private static Set<String> buildAllowedIssuers(String primary) {
        String normalizedPrimary = normalize(primary);
        Set<String> issuers = new LinkedHashSet<>();
        issuers.add(normalizedPrimary);
        addHostVariants(issuers, normalizedPrimary, "localhost");
        addHostVariants(issuers, normalizedPrimary, "127.0.0.1");
        addHostVariants(issuers, normalizedPrimary, "10.0.2.2");
        addHostVariants(issuers, normalizedPrimary, "host.docker.internal");
        addHostVariants(issuers, normalizedPrimary, "keycloak");
        Set<String> normalized = new LinkedHashSet<>();
        for (String issuer : issuers) {
            normalized.add(normalize(issuer));
        }
        return normalized;
    }

    private static void addHostVariants(Set<String> issuers, String base, String host) {
        for (String existingHost : List.of("localhost", "127.0.0.1", "10.0.2.2", "host.docker.internal", "keycloak")) {
            issuers.add(base.replace(existingHost, host));
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("/+$", "");
    }

    static String normalizeRole(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        return converter;
    }

    static class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<String> roles = new LinkedHashSet<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof Collection<?> collection) {
                    for (Object role : collection) {
                        String normalized = normalizeRole(String.valueOf(role));
                        if (normalized != null && !normalized.isBlank()) {
                            roles.add(normalized);
                        }
                    }
                }
            }

            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                for (Object clientObj : resourceAccess.values()) {
                    if (clientObj instanceof Map<?, ?> clientMap) {
                        Object clientRolesObj = clientMap.get("roles");
                        if (clientRolesObj instanceof Collection<?> collection) {
                            for (Object role : collection) {
                                String normalized = normalizeRole(String.valueOf(role));
                                if (normalized != null && !normalized.isBlank()) {
                                    roles.add(normalized);
                                }
                            }
                        }
                    }
                }
            }

            Collection<GrantedAuthority> authorities = new ArrayList<>();
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            return authorities;
        }
    }
}
