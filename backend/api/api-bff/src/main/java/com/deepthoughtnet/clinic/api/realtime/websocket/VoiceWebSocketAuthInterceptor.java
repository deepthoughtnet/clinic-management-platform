package com.deepthoughtnet.clinic.api.realtime.websocket;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
    private final JwtDecoder jwtDecoder;

    public VoiceWebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, java.util.Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        String tenantId = servletRequest.getServletRequest().getParameter("tenantId");
        if (token == null || token.isBlank() || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("tenantId", tenantId);
            attributes.put("sub", jwt.getSubject());
            attributes.put("roles", parseRoles(jwt));
            return true;
        } catch (Exception ex) {
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
        Arrays.asList("PLATFORM_ADMIN", "CLINIC_ADMIN", "AUDITOR", "RECEPTIONIST").forEach(allowed -> {
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
