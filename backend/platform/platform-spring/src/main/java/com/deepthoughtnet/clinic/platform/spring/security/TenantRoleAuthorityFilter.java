package com.deepthoughtnet.clinic.platform.spring.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TenantRoleAuthorityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        try {
            var ctx = RequestContextHolder.get();
            var auth = SecurityContextHolder.getContext().getAuthentication();

            if (ctx != null && auth instanceof AbstractAuthenticationToken token && token.isAuthenticated()) {
                String role = normalizeRole(ctx.tenantRole());
                if (role != null && !role.isBlank()) {
                    List<SimpleGrantedAuthority> merged = new ArrayList<>();
                    auth.getAuthorities().forEach(a -> merged.add(new SimpleGrantedAuthority(a.getAuthority())));
                    merged.add(new SimpleGrantedAuthority("ROLE_" + role));

                    token.setAuthenticated(true);
                    token.setDetails(auth.getDetails());

                    AbstractAuthenticationToken newAuth = new AbstractAuthenticationToken(merged) {
                        @Override
                        public Object getCredentials() {
                            return token.getCredentials();
                        }

                        @Override
                        public Object getPrincipal() {
                            return token.getPrincipal();
                        }
                    };
                    newAuth.setDetails(token.getDetails());
                    newAuth.setAuthenticated(true);
                    SecurityContextHolder.getContext().setAuthentication(newAuth);
                }
            }

            chain.doFilter(request, response);
        } finally {
            // nothing
        }
    }

    private static String normalizeRole(String value) {
        if (value == null) {
            return null;
        }
        return value
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}