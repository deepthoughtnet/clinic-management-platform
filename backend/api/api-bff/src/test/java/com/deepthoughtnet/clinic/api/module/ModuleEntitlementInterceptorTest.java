package com.deepthoughtnet.clinic.api.module;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
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
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/carepilot/campaigns");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verify(tenantRuntimeEntitlementProvider).requireModuleEnabled(tenantId, "CAREPILOT");
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }

    @Test
    void requiresReportsModuleForReportsPath() {
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/reports/summary");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verify(tenantRuntimeEntitlementProvider).requireModuleEnabled(tenantId, "REPORTS");
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }

    @Test
    void requiresPatientsModuleForPatientsPath() {
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/patients");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verify(tenantRuntimeEntitlementProvider).requireModuleEnabled(tenantId, "PATIENTS");
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }

    @Test
    void onlyChecksTenantActivityForUnmappedPath() {
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/health");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }

    @Test
    void allowsDeterministicAiStatusReadWithoutModuleCheck() {
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/ai/status");
        when(request.getMethod()).thenReturn("GET");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }

    @Test
    void allowsDeterministicClinicalContextReadWithoutModuleCheck() {
        TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider = mock(TenantRuntimeEntitlementProvider.class);
        ModuleRouteRegistry registry = new ModuleRouteRegistry();
        ModuleEntitlementInterceptor interceptor = new ModuleEntitlementInterceptor(tenantRuntimeEntitlementProvider, registry);
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/ai/clinical-context");
        when(request.getMethod()).thenReturn("GET");

        interceptor.preHandle(request, response, new Object());

        verify(tenantRuntimeEntitlementProvider).requireTenantActive(tenantId);
        verifyNoMoreInteractions(tenantRuntimeEntitlementProvider);
    }
}
