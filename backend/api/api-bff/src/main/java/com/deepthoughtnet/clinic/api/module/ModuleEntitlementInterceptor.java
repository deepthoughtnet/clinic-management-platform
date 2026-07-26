package com.deepthoughtnet.clinic.api.module;

import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ModuleEntitlementInterceptor implements HandlerInterceptor {
    private final TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider;
    private final ModuleRouteRegistry routeRegistry;

    public ModuleEntitlementInterceptor(
            TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider,
            ModuleRouteRegistry routeRegistry
    ) {
        this.tenantRuntimeEntitlementProvider = tenantRuntimeEntitlementProvider;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var tenantId = RequestContextHolder.requireTenantId();
        tenantRuntimeEntitlementProvider.requireTenantActive(tenantId);
        if (isDeterministicAiRead(request)) {
            return true;
        }
        String moduleKey = routeRegistry.moduleForPath(request.getRequestURI());
        if (moduleKey == null) {
            return true;
        }
        tenantRuntimeEntitlementProvider.requireModuleEnabled(tenantId, moduleKey);
        return true;
    }

    private boolean isDeterministicAiRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        return "/api/ai/status".equals(normalized) || "/api/ai/clinical-context".equals(normalized);
    }
}
