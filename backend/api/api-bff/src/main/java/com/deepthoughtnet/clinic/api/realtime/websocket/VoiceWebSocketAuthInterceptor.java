package com.deepthoughtnet.clinic.api.realtime.websocket;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Set<String> roles = parseRoles(jwt);
            attributes.put("tenantId", tenantId);
            attributes.put("sub", jwt.getSubject());
            attributes.put("roles", roles);
            log.info("voice.websocket.auth.success path={} tenantId={} subject={} roles={}",
                    path,
                    tenantId,
                    jwt.getSubject(),
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
                collection.forEach(role -> roles.add(String.valueOf(role).toUpperCase(java.util.Locale.ROOT)));
            }
        }
        Arrays.asList("PLATFORM_ADMIN", "TENANT_ADMIN", "CLINIC_ADMIN", "AUDITOR", "RECEPTIONIST").forEach(allowed -> {
            if (roles.contains("ROLE_" + allowed)) {
                roles.add(allowed);
            }
        });
        return roles;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
