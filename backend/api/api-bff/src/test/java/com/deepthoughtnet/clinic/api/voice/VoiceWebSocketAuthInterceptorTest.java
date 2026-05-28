package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.realtime.websocket.VoiceWebSocketAuthInterceptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class VoiceWebSocketAuthInterceptorTest {

    @Test
    void missingTokenOrTenantIsRejected() {
        VoiceWebSocketAuthInterceptor interceptor = new VoiceWebSocketAuthInterceptor(mock(JwtDecoder.class));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/voice/test");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                attributes
        );

        assertThat(accepted).isFalse();
    }

    @Test
    void validTokenPopulatesTenantAndRoles() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token-1")).thenReturn(
                Jwt.withTokenValue("token-1")
                        .header("alg", "none")
                        .claim("sub", "user-sub")
                        .claim("preferred_username", "receptionist@example.com")
                        .claim("realm_access", Map.of("roles", List.of("clinic:RECEPTIONIST")))
                        .claim("resource_access", Map.of(
                                "clinic-web", Map.of("roles", List.of("ROLE_TENANT_ADMIN"))
                        ))
                        .build()
        );
        VoiceWebSocketAuthInterceptor interceptor = new VoiceWebSocketAuthInterceptor(decoder);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/voice/test");
        servletRequest.setParameter("token", "token-1");
        servletRequest.setParameter("tenantId", "11111111-1111-1111-1111-111111111111");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("tenantId", "11111111-1111-1111-1111-111111111111");
        assertThat(attributes).containsEntry("sub", "user-sub");
        assertThat(attributes).containsEntry("userIdentifier", "receptionist@example.com");
        assertThat((Set<String>) attributes.get("roles")).contains("RECEPTIONIST", "TENANT_ADMIN");
    }

    @Test
    void invalidTenantIsRejected() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token-1")).thenReturn(
                Jwt.withTokenValue("token-1")
                        .header("alg", "none")
                        .claim("sub", "user-sub")
                        .claim("realm_access", Map.of("roles", List.of("ROLE_CLINIC_ADMIN")))
                        .build()
        );
        VoiceWebSocketAuthInterceptor interceptor = new VoiceWebSocketAuthInterceptor(decoder);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/voice/test");
        servletRequest.setParameter("token", "token-1");
        servletRequest.setParameter("tenantId", "tenant-a");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                attributes
        );

        assertThat(accepted).isFalse();
    }
}
