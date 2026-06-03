package com.deepthoughtnet.clinic.api.patientportal;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

class PatientPortalSecurityIntegrationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PatientPortalController controller = new PatientPortalController(
                mock(PatientPortalService.class),
                mock(PatientPortalCareAiService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .addFilters(new RejectAnonymousFilter())
                .build();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/patient-portal/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(get("/api/patient-portal/doctors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(get("/api/patient-portal/doctors/123e4567-e89b-12d3-a456-426614174000/slots")
                        .param("date", "2026-06-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(post("/api/patient-portal/appointments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "publicDoctorId": "123e4567-e89b-12d3-a456-426614174000",
                                  "appointmentDate": "2026-06-10",
                                  "appointmentTime": "10:30:00"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(post("/api/patient-portal/careai/message")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Book Dr Suresh tomorrow morning"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(post("/api/patient-portal/careai/reset"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    private static final class RejectAnonymousFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("""
                        {"code":"unauthorized","message":"Authentication is required"}
                        """.trim());
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
