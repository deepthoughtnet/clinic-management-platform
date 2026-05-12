package com.deepthoughtnet.clinic.api.module;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModuleEntitlementInterceptorTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void requiresCarePilotModuleForCarePilotPath() {
        TenantSubscriptionService tenantSubscriptionService = mock(TenantSubscriptionService.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantSubscriptionService, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/carepilot/campaigns");

        interceptor.preHandle(request, response, new Object());

        verify(tenantSubscriptionService).requireTenantActive(tenantId);
        verify(tenantSubscriptionService).requireModuleEnabled(tenantId, "CAREPILOT");
        verifyNoMoreInteractions(tenantSubscriptionService);
    }

    @Test
    void onlyChecksTenantActivityForUnmappedPath() {
        TenantSubscriptionService tenantSubscriptionService = mock(TenantSubscriptionService.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantSubscriptionService, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/health");

        interceptor.preHandle(request, response, new Object());

        verify(tenantSubscriptionService).requireTenantActive(tenantId);
        verifyNoMoreInteractions(tenantSubscriptionService);
    }
}
