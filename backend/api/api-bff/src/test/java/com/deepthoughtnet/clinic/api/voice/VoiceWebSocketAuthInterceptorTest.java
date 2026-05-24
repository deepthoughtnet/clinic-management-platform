package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.realtime.websocket.VoiceWebSocketAuthInterceptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                        .claim("realm_access", Map.of("roles", List.of("clinic_admin", "receptionist")))
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

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("tenantId", "tenant-a");
        assertThat(attributes).containsEntry("sub", "user-sub");
        assertThat((java.util.Set<String>) attributes.get("roles")).contains("CLINIC_ADMIN", "RECEPTIONIST");
    }
}
