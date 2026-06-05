package com.deepthoughtnet.clinic.api.patientportal.voice;

import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionPrincipal;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class PatientPortalVoiceWebSocketAuthInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalVoiceWebSocketAuthInterceptor.class);
    private static final String SESSION_TOKEN_PARAM = "sessionToken";

    private final PatientPortalSessionTokenService sessionTokenService;
    private final AppUserProvisioner appUserProvisioner;

    public PatientPortalVoiceWebSocketAuthInterceptor(
            PatientPortalSessionTokenService sessionTokenService,
            AppUserProvisioner appUserProvisioner
    ) {
        this.sessionTokenService = sessionTokenService;
        this.appUserProvisioner = appUserProvisioner;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            java.util.Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("patient.voice.websocket.auth.failed reason=non-servlet-request");
            return false;
        }

        String path = servletRequest.getServletRequest().getRequestURI();
        String sessionToken = servletRequest.getServletRequest().getParameter(SESSION_TOKEN_PARAM);
        if (sessionToken == null || sessionToken.isBlank()) {
            log.warn("patient.voice.websocket.auth.failed path={} reason=missing-session-token", path);
            return false;
        }

        PatientPortalSessionPrincipal principal = sessionTokenService.parse(sessionToken);
        if (principal == null || principal.tenantId() == null || principal.patientId() == null) {
            log.warn("patient.voice.websocket.auth.failed path={} reason=invalid-session", path);
            return false;
        }
        if (principal.roles() == null || !principal.roles().contains("PATIENT")) {
            log.warn("patient.voice.websocket.auth.failed path={} tenantId={} patientId={} reason=invalid-role roles={}",
                    path, principal.tenantId(), principal.patientId(), principal.roles());
            return false;
        }

        UUID appUserId = appUserProvisioner.upsertAndReturnId(
                principal.tenantId(),
                principal.subject(),
                null,
                principal.displayName()
        );

        attributes.put("tenantId", principal.tenantId().toString());
        attributes.put("patientId", principal.patientId().toString());
        attributes.put("appUserId", appUserId.toString());
        attributes.put("sub", principal.subject());
        attributes.put("displayName", principal.displayName());
        attributes.put("phone", principal.phone());
        attributes.put("roles", Set.of("PATIENT"));
        log.info("patient.voice.websocket.auth.success path={} tenantId={} patientId={} appUserId={}",
                path, principal.tenantId(), principal.patientId(), appUserId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
