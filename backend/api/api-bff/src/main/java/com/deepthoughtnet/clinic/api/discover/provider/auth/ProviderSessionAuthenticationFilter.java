package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderSessionEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ProviderSessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_COOKIE = "JEEVANAM_PROVIDER_SESSION";

    private final DiscoverVerificationService verificationService;

    public ProviderSessionAuthenticationFilter(DiscoverVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null && isProviderRequest(request)) {
                String sessionToken = readCookie(request, SESSION_COOKIE);
                verificationService.resolveSession(sessionToken).ifPresent(session -> {
                    ProviderSessionPrincipal principal = toPrincipal(session);
                    var authorities = principal.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(principal, sessionToken, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }

            filterChain.doFilter(request, response);
        } finally {
            if (isProviderRequest(request)) {
                var current = SecurityContextHolder.getContext().getAuthentication();
                if (current != null && current.getPrincipal() instanceof ProviderSessionPrincipal) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
    }

    private ProviderSessionPrincipal toPrincipal(DiscoverProviderSessionEntity session) {
        return new ProviderSessionPrincipal(session.getProviderAccountId(), session.getId(), Set.of("PROVIDER"));
    }

    private boolean isProviderRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null
                && uri.startsWith("/api/provider/")
                && !uri.startsWith("/api/provider/auth/");
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
