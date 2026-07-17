package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MedicationSafetyControllerTest {
    private final MedicationSafetyService medicationSafetyService = mock(MedicationSafetyService.class);
    private final MedicationSafetyReviewService medicationSafetyReviewService = mock(MedicationSafetyReviewService.class);
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void getEvaluateIsReadOnlyAndBasePostIsNotMapped() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MedicationSafetyController(medicationSafetyService, medicationSafetyReviewService, doctorAssignmentSecurityService)
        ).build();

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorAppUserId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorAppUserId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        when(medicationSafetyService.evaluateForConsultation(tenantId, consultationId, actorAppUserId)).thenReturn(sampleEvaluation());

        mockMvc.perform(get("/api/consultations/{consultationId}/prescription/safety", consultationId))
                .andExpect(status().isOk());

        verify(medicationSafetyService).evaluateForConsultation(tenantId, consultationId, actorAppUserId);
        verify(medicationSafetyReviewService, never()).evaluateAndPersist(tenantId, consultationId, actorAppUserId);

        mockMvc.perform(post("/api/consultations/{consultationId}/prescription/safety", consultationId))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void runSafetyCheckUsesExplicitCommandEndpoint() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MedicationSafetyController(medicationSafetyService, medicationSafetyReviewService, doctorAssignmentSecurityService)
        ).build();

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorAppUserId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorAppUserId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        when(medicationSafetyReviewService.runSafetyCheck(tenantId, consultationId, actorAppUserId)).thenReturn(new MedicationSafetyReviewResponse(
                UUID.randomUUID(),
                consultationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "eval-1",
                "prescription-hash",
                "patient-context-hash",
                "med-safety-v1",
                MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name(),
                false,
                true,
                "NONE",
                null,
                actorAppUserId,
                "Dr Demo",
                null,
                MedicationSafetySeverity.INFO.name(),
                0,
                0,
                0,
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/consultations/{consultationId}/prescription/safety/run", consultationId))
                .andExpect(status().isOk());

        verify(medicationSafetyReviewService).runSafetyCheck(tenantId, consultationId, actorAppUserId);
        verify(medicationSafetyService, never()).evaluateForConsultation(tenantId, consultationId, actorAppUserId);
    }

    @Test
    void runSafetyCheckReturnsControlledValidationWhenPrescriptionNotSaved() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MedicationSafetyController(medicationSafetyService, medicationSafetyReviewService, doctorAssignmentSecurityService)
        ).setControllerAdvice(new GlobalRestExceptionHandler()).build();

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorAppUserId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorAppUserId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        when(medicationSafetyReviewService.runSafetyCheck(tenantId, consultationId, actorAppUserId)).thenThrow(new MedicationSafetyGuardException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "PRESCRIPTION_NOT_SAVED",
                "Save the prescription before running Medication Safety.",
                null,
                null,
                null,
                List.of()
        ));

        mockMvc.perform(post("/api/consultations/{consultationId}/prescription/safety/run", consultationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_NOT_SAVED"))
                .andExpect(jsonPath("$.message").value("Save the prescription before running Medication Safety."));
    }

    private MedicationSafetyEvaluationResult sampleEvaluation() {
        return new MedicationSafetyEvaluationResult(
                "eval-1",
                java.time.OffsetDateTime.now(),
                UUID.randomUUID(),
                MedicationSafetySeverity.INFO,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, true, true, "EVALUATED"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "DRAFT"
                )
        );
    }
}
