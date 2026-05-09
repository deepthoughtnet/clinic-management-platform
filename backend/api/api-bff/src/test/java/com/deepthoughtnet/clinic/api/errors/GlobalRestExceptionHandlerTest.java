package com.deepthoughtnet.clinic.api.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalRestExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void formatsValidationErrorsWithStandardEnvelope() throws Exception {
        mockMvc.perform(post("/validation")
                        .header("X-Correlation-Id", "corr-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("name: must not be blank"))
                .andExpect(jsonPath("$.path").value("/validation"))
                .andExpect(jsonPath("$.correlationId").value("corr-123"))
                .andExpect(jsonPath("$.requestId").value("corr-123"));
    }

    @Test
    void formatsUnauthorizedAccessWithStandardEnvelope() throws Exception {
        mockMvc.perform(get("/unauthorized")
                        .header("X-Correlation-Id", "corr-456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.correlationId").value("corr-456"));
    }

    @Test
    void formatsForbiddenAccessWithStandardEnvelope() throws Exception {
        mockMvc.perform(get("/forbidden")
                        .header("X-Correlation-Id", "corr-789"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"))
                .andExpect(jsonPath("$.correlationId").value("corr-789"));
    }

    @RestController
    static class TestController {
        @PostMapping("/validation")
        void validation(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Authentication is required");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Denied");
        }
    }

    record Payload(@NotBlank String name) {
    }
}
