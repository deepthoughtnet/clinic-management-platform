package com.deepthoughtnet.clinic.platform.spring.web;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import com.deepthoughtnet.clinic.platform.spring.context.CorrelationId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.spring.context.TenantHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class RequestContextFilter extends OncePerRequestFilter implements Ordered {

    public static final String PLATFORM_OP_HEADER = "X-Platform-Op";

    private final AuthContextExtractor auth;
    private final AppUserProvisioner provisioner;
    private final TenantRoleResolver roleResolver;

    public RequestContextFilter(AuthContextExtractor auth, AppUserProvisioner provisioner, TenantRoleResolver roleResolver) {
        this.auth = auth;
        this.provisioner = provisioner;
        this.roleResolver = roleResolver;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String corr = CorrelationId.ensure(request.getHeader(CorrelationId.HEADER));
        MDC.put(CorrelationId.MDC_KEY, corr);
        response.setHeader(CorrelationId.HEADER, corr);

        try {
            String sub = safe(auth.keycloakSub());
            if (sub == null) {
                // Unauthenticated; let Spring Security handle it (401 etc).
                chain.doFilter(request, response);
                return;
            }

            Set<String> tokenRoles = auth.rolesUpper();
            boolean isPlatformAdmin = tokenRoles != null && tokenRoles.contains("PLATFORM_ADMIN");

            String tenantHeader = request.getHeader(TenantHeaders.TENANT_HEADER);
            TenantId tenantId = auth.resolveTenantId(tenantHeader);

            // Platform op gate:
            boolean platformOp = isExplicitPlatformOperation(request);
            if (platformOp && !isPlatformAdmin) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "X-Platform-Op requires PLATFORM_ADMIN");
                return;
            }

            // Tenant not resolved:
            if (tenantId == null) {
                if ((platformOp && isPlatformAdmin) || isTenantlessMeRequest(request)) {
                    // Tenantless platform ops and /api/me bootstrap are allowed.
                    RequestContextHolder.set(new RequestContext(
                            null,
                            null,
                            sub,
                            tokenRoles,
                            null,
                            corr
                    ));
                    chain.doFilter(request, response);
                    return;
                }

                // ✅ Do NOT throw -> return clean 401
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Tenant not resolved (missing tenant_id claim or X-Tenant-Id header)"
                );
                return;
            }

            // Tenant-scoped normal flow
            UUID appUserId = provisioner.upsertAndReturnId(
                    tenantId.value(),
                    sub,
                    auth.email(),
                    auth.displayName()
            );

            String tenantRole = roleResolver.resolveTenantRole(tenantId.value(), appUserId, tokenRoles);

            RequestContextHolder.set(new RequestContext(
                    tenantId,
                    appUserId,
                    sub,
                    tokenRoles,
                    tenantRole,
                    corr
            ));

            chain.doFilter(request, response);

        } finally {
            RequestContextHolder.clear();
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private boolean isExplicitPlatformOperation(HttpServletRequest request) {
        String v = request.getHeader(PLATFORM_OP_HEADER);
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    private boolean isTenantlessMeRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) && "/api/me".equals(request.getRequestURI());
    }

    private static String safe(String v) {
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }
}
