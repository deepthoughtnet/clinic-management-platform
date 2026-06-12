package com.deepthoughtnet.clinic.api.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityConflictException;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

    @Test
    void returnsPlainTextForbiddenForPdfAcceptHeader() throws Exception {
        mockMvc.perform(get("/forbidden")
                        .header("X-Correlation-Id", "corr-pdf")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("You do not have permission to perform this action"));
    }

    @Test
    void formatsDoctorAvailabilityConflictWithStandardEnvelope() throws Exception {
        mockMvc.perform(get("/availability-conflict")
                        .header("X-Correlation-Id", "corr-999"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Availability already exists for this doctor, day, and time range."))
                .andExpect(jsonPath("$.correlationId").value("corr-999"));
    }

    @Test
    void redactsUuidReferencesFromUserFacingErrors() throws Exception {
        mockMvc.perform(get("/uuid-error")
                        .header("X-Correlation-Id", "corr-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid appointment"));
    }

    @Test
    void formatsJsonMappingErrorsWithFieldPath() throws Exception {
        mockMvc.perform(post("/json-mapping")
                        .header("X-Correlation-Id", "corr-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medicines":[{"timing":{"value":"AFTER_FOOD"}}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("invalid_json"))
                .andExpect(jsonPath("$.message").value("Invalid value for medicines[0].timing"))
                .andExpect(jsonPath("$.correlationId").value("corr-json"));
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

        @GetMapping("/availability-conflict")
        void availabilityConflict() {
            throw new DoctorAvailabilityConflictException("Availability already exists for this doctor, day, and time range.");
        }

        @GetMapping("/uuid-error")
        void uuidError() {
            throw new IllegalArgumentException("Invalid appointment ref: 123e4567-e89b-12d3-a456-426614174000");
        }

        @PostMapping("/json-mapping")
        void jsonMapping(@RequestBody JsonMappingPayload payload) {
        }
    }

    record Payload(@NotBlank String name) {
    }

    record JsonMappingPayload(@NotNull List<MedicinePayload> medicines) {
    }

    record MedicinePayload(@NotNull Timing timing) {
    }
}
