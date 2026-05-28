package com.deepthoughtnet.clinic.api.realtime.websocket;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * JWT query-token authentication for websocket foundation where browser cannot set auth headers reliably.
 */
@Component
public class VoiceWebSocketAuthInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketAuthInterceptor.class);
    private final JwtDecoder jwtDecoder;

    public VoiceWebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, java.util.Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("voice.websocket.auth.failed reason=non-servlet-request");
            return false;
        }
        String path = servletRequest.getServletRequest().getRequestURI();
        String token = servletRequest.getServletRequest().getParameter("token");
        String tenantId = servletRequest.getServletRequest().getParameter("tenantId");
        log.info("voice.websocket.connect.attempt path={} tenantIdPresent={} tokenPresent={}",
                path,
                tenantId != null && !tenantId.isBlank(),
                token != null && !token.isBlank());
        if (token == null || token.isBlank() || tenantId == null || tenantId.isBlank()) {
            log.warn("voice.websocket.auth.failed path={} reason=missing-token-or-tenant", path);
            return false;
        }
        String normalizedTenantId = normalizeTenantId(tenantId);
        if (normalizedTenantId == null) {
            log.warn("voice.websocket.auth.failed path={} tenantId={} reason=invalid-tenant", path, tenantId);
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Set<String> roles = parseRoles(jwt);
            String userIdentifier = resolveUserIdentifier(jwt);
            attributes.put("tenantId", normalizedTenantId);
            attributes.put("sub", jwt.getSubject());
            attributes.put("userIdentifier", userIdentifier);
            attributes.put("roles", roles);
            log.info("voice.websocket.auth.success path={} tenantId={} subject={} userIdentifier={} roles={}",
                    path,
                    normalizedTenantId,
                    jwt.getSubject(),
                    userIdentifier,
                    roles);
            return true;
        } catch (Exception ex) {
            log.warn("voice.websocket.auth.failed path={} tenantId={} reason={}",
                    path,
                    tenantId,
                    ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseRoles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (realmAccessObj instanceof java.util.Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof java.util.Collection<?> collection) {
                collection.forEach(role -> addNormalizedRole(roles, role));
            }
        }
        Object resourceAccessObj = jwt.getClaims().get("resource_access");
        if (resourceAccessObj instanceof Map<?, ?> resourceAccess) {
            for (Object clientAccessObj : resourceAccess.values()) {
                if (clientAccessObj instanceof Map<?, ?> clientAccess) {
                    Object rolesObj = clientAccess.get("roles");
                    if (rolesObj instanceof java.util.Collection<?> collection) {
                        collection.forEach(role -> addNormalizedRole(roles, role));
                    }
                }
            }
        }
        return roles;
    }

    private void addNormalizedRole(Set<String> roles, Object rawRole) {
        if (rawRole == null) {
            return;
        }
        String raw = String.valueOf(rawRole).trim();
        if (raw.isBlank()) {
            return;
        }
        addNormalizedRoleVariant(roles, raw);
        int colonIndex = raw.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < raw.length() - 1) {
            addNormalizedRoleVariant(roles, raw.substring(colonIndex + 1));
        }
    }

    private void addNormalizedRoleVariant(Set<String> roles, String rawRole) {
        String normalized = normalizeRole(rawRole);
        if (normalized != null && !normalized.isBlank()) {
            roles.add(normalized);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private String normalizeTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId.trim()).toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveUserIdentifier(Jwt jwt) {
        return Arrays.asList(
                        jwt.getClaimAsString("preferred_username"),
                        jwt.getClaimAsString("email"),
                        jwt.getSubject())
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
