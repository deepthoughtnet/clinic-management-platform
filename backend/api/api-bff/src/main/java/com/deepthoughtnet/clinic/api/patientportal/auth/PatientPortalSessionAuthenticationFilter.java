package com.deepthoughtnet.clinic.api.patientportal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PatientPortalSessionAuthenticationFilter extends OncePerRequestFilter {

    private final PatientPortalSessionTokenService sessionTokenService;

    public PatientPortalSessionAuthenticationFilter(PatientPortalSessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null && isPatientPortalRequest(request)) {
                String sessionToken = request.getHeader(PatientPortalSessionTokenService.SESSION_HEADER);
                PatientPortalSessionPrincipal principal = sessionTokenService.parse(sessionToken);
                if (principal != null) {
                    var authorities = principal.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(principal, sessionToken, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            if (isPatientPortalRequest(request)) {
                var current = SecurityContextHolder.getContext().getAuthentication();
                if (current != null && current.getPrincipal() instanceof PatientPortalSessionPrincipal) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
    }

    private boolean isPatientPortalRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null
                && uri.startsWith("/api/patient-portal/")
                && !uri.startsWith("/api/patient-portal/auth/");
    }
}
