package com.deepthoughtnet.clinic.api.module;

import com.deepthoughtnet.clinic.identity.service.TenantModuleEntitlementService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ModuleEntitlementInterceptor implements HandlerInterceptor {
    private final TenantModuleEntitlementService entitlementService;
    private final ModuleRouteRegistry routeRegistry;

    public ModuleEntitlementInterceptor(
            TenantModuleEntitlementService entitlementService,
            ModuleRouteRegistry routeRegistry
    ) {
        this.entitlementService = entitlementService;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String moduleKey = routeRegistry.moduleForPath(request.getRequestURI());
        if (moduleKey == null) {
            return true;
        }
        entitlementService.requireModuleEnabled(RequestContextHolder.requireTenantId(), moduleKey);
        return true;
    }
}
