package com.deepthoughtnet.clinic.platform.spring.web;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import com.deepthoughtnet.clinic.platform.spring.context.CorrelationId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.spring.context.TenantHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class RequestContextFilter extends OncePerRequestFilter implements Ordered {

    public static final String PLATFORM_OP_HEADER = "X-Platform-Op";

    private final AuthContextExtractor auth;
    private final AppUserProvisioner provisioner;
    private final TenantRoleResolver roleResolver;
    private final ObjectMapper objectMapper;

    public RequestContextFilter(AuthContextExtractor auth, AppUserProvisioner provisioner, TenantRoleResolver roleResolver, ObjectMapper objectMapper) {
        this.auth = auth;
        this.provisioner = provisioner;
        this.roleResolver = roleResolver;
        this.objectMapper = objectMapper;
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
            if (log.isDebugEnabled()) {
                log.debug(
                        "Request context bootstrap path={} method={} sub={} email={} tenantHeader={} resolvedTenant={}",
                        request.getRequestURI(),
                        request.getMethod(),
                        sub,
                        auth.email(),
                        tenantHeader,
                        tenantId == null ? null : tenantId.value()
                );
            }

            // Platform op gate:
            boolean platformOp = isExplicitPlatformOperation(request);
            if (platformOp && !isPlatformAdmin) {
                writeError(response, request, HttpServletResponse.SC_FORBIDDEN, "forbidden", "You do not have permission to perform this action");
                return;
            }

            // Tenant not resolved:
            if (tenantId == null) {
                if (tenantHeader != null && !tenantHeader.isBlank()) {
                    writeError(response, request, HttpServletResponse.SC_BAD_REQUEST, "bad_request", "Invalid tenant context. Please reselect clinic.");
                    return;
                }

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
                writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Tenant not resolved. Select a clinic tenant first.");
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
        if (v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"))) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && ("/api/platform".equals(uri) || uri.startsWith("/api/platform/"));
    }

    private boolean isTenantlessMeRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) && "/api/me".equals(request.getRequestURI());
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType("application/json");
        String correlationId = CorrelationId.ensure(request.getHeader(CorrelationId.HEADER));
        response.setHeader(CorrelationId.HEADER, correlationId);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.OffsetDateTime.now().toString());
        body.put("path", request.getRequestURI());
        body.put("status", status);
        body.put("code", code);
        body.put("message", message);
        body.put("correlationId", correlationId);
        body.put("requestId", correlationId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String safe(String v) {
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }
}
