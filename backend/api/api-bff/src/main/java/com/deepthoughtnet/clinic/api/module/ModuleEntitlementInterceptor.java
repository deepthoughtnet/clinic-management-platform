package com.deepthoughtnet.clinic.api.module;

import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ModuleEntitlementInterceptor implements HandlerInterceptor {
    private final TenantSubscriptionService tenantSubscriptionService;
    private final ModuleRouteRegistry routeRegistry;

    public ModuleEntitlementInterceptor(
            TenantSubscriptionService tenantSubscriptionService,
            ModuleRouteRegistry routeRegistry
    ) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var tenantId = RequestContextHolder.requireTenantId();
        tenantSubscriptionService.requireTenantActive(tenantId);
        String moduleKey = routeRegistry.moduleForPath(request.getRequestURI());
        if (moduleKey == null) {
            return true;
        }
        tenantSubscriptionService.requireModuleEnabled(tenantId, moduleKey);
        return true;
    }
}
