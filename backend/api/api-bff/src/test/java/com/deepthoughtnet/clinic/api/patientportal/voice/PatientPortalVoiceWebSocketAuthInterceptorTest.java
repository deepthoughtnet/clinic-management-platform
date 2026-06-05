package com.deepthoughtnet.clinic.api.patientportal.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalAuthProperties;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class PatientPortalVoiceWebSocketAuthInterceptorTest {

    @Test
    void patientSessionIsAcceptedAndFrontendPatientIdIsIgnored() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        PatientPortalAuthProperties properties = new PatientPortalAuthProperties();
        properties.setSessionSecret("voice-patient-secret");
        PatientPortalSessionTokenService tokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        var provisioner = mock(com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner.class);
        when(provisioner.upsertAndReturnId(tenantId, "patientportal:" + tenantId + ":" + patientId, null, "Riya Sharma")).thenReturn(appUserId);
        PatientPortalVoiceWebSocketAuthInterceptor interceptor = new PatientPortalVoiceWebSocketAuthInterceptor(tokenService, provisioner);

        String token = tokenService.issuePatientToken("patientportal:" + tenantId + ":" + patientId, tenantId, patientId, "Riya Sharma");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/patient-portal/careai");
        request.setParameter("sessionToken", token);
        request.setParameter("patientId", UUID.randomUUID().toString());
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo(tenantId.toString());
        assertThat(attributes.get("patientId")).isEqualTo(patientId.toString());
        assertThat(attributes.get("appUserId")).isEqualTo(appUserId.toString());
    }

    @Test
    void registrationSessionIsRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        PatientPortalAuthProperties properties = new PatientPortalAuthProperties();
        properties.setSessionSecret("voice-registration-secret");
        PatientPortalSessionTokenService tokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        PatientPortalVoiceWebSocketAuthInterceptor interceptor = new PatientPortalVoiceWebSocketAuthInterceptor(
                tokenService,
                mock(com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner.class)
        );

        String token = tokenService.issueRegistrationToken("patientportal:registration:" + tenantId, tenantId, "9876543210", "New Patient");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/patient-portal/careai");
        request.setParameter("sessionToken", token);

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
    }

    @Test
    void missingSessionTokenIsRejected() throws Exception {
        PatientPortalAuthProperties properties = new PatientPortalAuthProperties();
        properties.setSessionSecret("voice-missing-secret");
        PatientPortalSessionTokenService tokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        PatientPortalVoiceWebSocketAuthInterceptor interceptor = new PatientPortalVoiceWebSocketAuthInterceptor(
                tokenService,
                mock(com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/patient-portal/careai");

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
    }

    @Test
    void malformedSessionTokenIsRejected() throws Exception {
        PatientPortalAuthProperties properties = new PatientPortalAuthProperties();
        properties.setSessionSecret("voice-invalid-secret");
        PatientPortalSessionTokenService tokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        PatientPortalVoiceWebSocketAuthInterceptor interceptor = new PatientPortalVoiceWebSocketAuthInterceptor(
                tokenService,
                mock(com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/patient-portal/careai");
        request.setParameter("sessionToken", "not-a-valid-token");

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
    }
}
