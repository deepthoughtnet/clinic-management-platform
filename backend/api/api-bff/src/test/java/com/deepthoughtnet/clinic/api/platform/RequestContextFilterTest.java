package com.deepthoughtnet.clinic.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.spring.web.RequestContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {
    private AuthContextExtractor auth;
    private AppUserProvisioner provisioner;
    private TenantRoleResolver roleResolver;
    private RequestContextFilter filter;

    @BeforeEach
    void setUp() {
        auth = mock(AuthContextExtractor.class);
        provisioner = mock(AppUserProvisioner.class);
        roleResolver = mock(TenantRoleResolver.class);
        filter = new RequestContextFilter(auth, provisioner, roleResolver, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void platformAdminCanUseTenantlessPlatformOperations() throws Exception {
        when(auth.keycloakSub()).thenReturn("sub-123");
        when(auth.email()).thenReturn("platform.admin@arogia.com");
        when(auth.displayName()).thenReturn("Platform Admin");
        when(auth.rolesUpper()).thenReturn(Set.of("PLATFORM_ADMIN"));
        when(auth.resolveTenantId(null)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/platform/tenants");
        request.addHeader(RequestContextFilter.PLATFORM_OP_HEADER, "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<RequestContext> capturedContext = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            capturedContext.set(RequestContextHolder.get());
        });

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get().tenantId()).isNull();
        assertThat(capturedContext.get().appUserId()).isNull();
        assertThat(capturedContext.get().tokenRoles()).contains("PLATFORM_ADMIN");
        verify(provisioner, never()).upsertAndReturnId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(roleResolver, never()).resolveTenantRole(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void platformOperationsWithoutPlatformAdminAreRejected() throws Exception {
        when(auth.keycloakSub()).thenReturn("sub-456");
        when(auth.email()).thenReturn("clinic.admin@arogia.com");
        when(auth.displayName()).thenReturn("Clinic Admin");
        when(auth.rolesUpper()).thenReturn(Set.of("CLINIC_ADMIN"));
        when(auth.resolveTenantId(null)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/platform/tenants");
        request.addHeader(RequestContextFilter.PLATFORM_OP_HEADER, "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("You do not have permission to perform this action");
    }
}
