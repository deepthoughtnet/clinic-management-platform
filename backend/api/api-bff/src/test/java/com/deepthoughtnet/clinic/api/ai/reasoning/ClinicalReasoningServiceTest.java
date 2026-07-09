package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClinicalReasoningServiceTest {
    @Test
    void returnsNotFoundWhenConsultationDoesNotExistForTenant() {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningEngine clinicalReasoningEngine = mock(ClinicalReasoningEngine.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, clinicalReasoningEngine);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(tenantId, consultationId, new ClinicalReasoningRequest(null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consultation not found");
    }

    @Test
    void rejectsPatientMismatchForConsultation() {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningEngine clinicalReasoningEngine = mock(ClinicalReasoningEngine.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, clinicalReasoningEngine);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, UUID.randomUUID(), UUID.randomUUID(), null);
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> service.generate(tenantId, consultationId, new ClinicalReasoningRequest(UUID.randomUUID(), null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Patient mismatch for consultation");
    }

    @Test
    void returnsCompactWrapperResponseByDefault() {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningEngine clinicalReasoningEngine = mock(ClinicalReasoningEngine.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, clinicalReasoningEngine);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse(
                tenantId,
                patientId,
                consultationId,
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.PatientSnapshot("Rohan Sharma", 42, "MALE", "Diabetes Mellitus", null, List.of(), "2026-01-08"),
                List.of(),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.MedicationSummary(List.of(), List.of(), List.of(), List.of(), List.of()),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.DiagnosisSummary(null, List.of()),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.IntakeSummary(true, "Fever", null, null, List.of(), null, null, null, null),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.LabIntelligence(null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.DocumentIntelligence(List.of(), List.of(), List.of(), List.of()),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.TimelineSummary(List.of(), null),
                new com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), null),
                null,
                null,
                "{}",
                OffsetDateTime.now()
        ));
        ClinicalReasoningResult reasoningResult = new ClinicalReasoningResult(
                consultationId,
                patientId,
                OffsetDateTime.now(),
                "GEMINI",
                "gemini-2.5-flash",
                "HIGH",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                new ClinicalReasoningResult.SourceContextSummary("Fever", List.of(), "INTAKE BP 136/86", List.of(), List.of(), List.of()),
                new ReasoningMetadata("engine.v1", "clinic.clinical.reasoning.v1", "v1", "v1", "GEMINI", "gemini-2.5-flash", java.util.Map.of(), "VALID", "req-1", "corr-1", 12L, false, "STOP", "COMPLETE", 321, "{\"ok\":true}", 321, null)
        );
        when(clinicalReasoningEngine.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(reasoningResult);

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        try {
            ClinicalReasoningResponse response = service.generate(tenantId, consultationId, new ClinicalReasoningRequest(patientId, "Fever", null, null, null, null, null, null, null, null), false);

            assertThat(response.reasoningResult()).isEqualTo(reasoningResult);
            assertThat(response.debug()).isNull();
            assertThat(response.metadata()).isNotNull();
        } finally {
            RequestContextHolder.clear();
        }
    }
}
