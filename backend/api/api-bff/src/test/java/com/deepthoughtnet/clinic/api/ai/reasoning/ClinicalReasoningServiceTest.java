package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.consultation.db.ClinicalReasoningResultEntity;
import com.deepthoughtnet.clinic.consultation.db.ClinicalReasoningResultRepository;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClinicalReasoningServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void returnsNotFoundWhenConsultationDoesNotExistForTenant() {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        ClinicalReasoningService service = new ClinicalReasoningService(
                consultationRepository,
                mock(ClinicalContextService.class),
                mock(ClinicalReasoningEngine.class),
                mock(ClinicalReasoningResultRepository.class),
                contextHasher,
                mock(TenantUserManagementService.class),
                mock(AuditEventPublisher.class),
                objectMapper
        );

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(tenantId, consultationId, new ClinicalReasoningRequest(null, null, null, null, null, null, null, null, null, null), false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consultation not found");
    }

    @Test
    void returnsNotGeneratedWhenNoPersistedReasoningExists() throws Exception {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningEngine clinicalReasoningEngine = mock(ClinicalReasoningEngine.class);
        ClinicalReasoningResultRepository reasoningResultRepository = mock(ClinicalReasoningResultRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, clinicalReasoningEngine, reasoningResultRepository, contextHasher, tenantUserManagementService, auditEventPublisher, objectMapper);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(null);
        when(reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(Optional.empty());

        ClinicalReasoningResponse response = service.get(tenantId, consultationId);

        assertThat(response.reasoningStatus()).isEqualTo("NOT_GENERATED");
        assertThat(response.reasoningResult()).isNull();
    }

    @Test
    void returnsCurrentPersistedReasoningWhenContextMatches() throws Exception {
        ClinicalReasoningResultRepository reasoningResultRepository = mock(ClinicalReasoningResultRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        ClinicalReasoningService service = new ClinicalReasoningService(
                mock(ConsultationRepository.class),
                mock(ClinicalContextService.class),
                mock(ClinicalReasoningEngine.class),
                reasoningResultRepository,
                contextHasher,
                mock(TenantUserManagementService.class),
                mock(AuditEventPublisher.class),
                objectMapper
        );

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalReasoningResult result = sampleReasoningResult(consultationId, patientId);
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        consultation.update("Fever", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalReasoningRequest persistedRequest = persistedRequest(patientId);
        String currentHash = contextHasher.contextHash(tenantId, consultation, null, persistedRequest);
        ClinicalReasoningResultEntity entity = ClinicalReasoningResultEntity.create(
                tenantId,
                consultationId,
                patientId,
                1,
                currentHash,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                "MOCK",
                "mock-model",
                UUID.randomUUID(),
                "Amit Verma",
                result.generatedAt(),
                objectMapper.writeValueAsString(result)
        );
        when(reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(Optional.of(entity));
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(null);
        service = new ClinicalReasoningService(consultationRepository, clinicalContextService, mock(ClinicalReasoningEngine.class), reasoningResultRepository, contextHasher, mock(TenantUserManagementService.class), mock(AuditEventPublisher.class), objectMapper);

        ClinicalReasoningResponse response = service.get(tenantId, consultationId);

        assertThat(response.reasoningStatus()).isEqualTo("CURRENT");
        assertThat(response.reasoningId()).isEqualTo(entity.getId().toString());
        assertThat(response.generatedByDisplayName()).isEqualTo("Amit Verma");
        assertThat(response.reasoningResult()).isNotNull();
        assertThat(response.reasoningResult().primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
    }

    @Test
    void marksReasoningStaleWhenContextHashChanges() throws Exception {
        ClinicalReasoningResultRepository reasoningResultRepository = mock(ClinicalReasoningResultRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, mock(ClinicalReasoningEngine.class), reasoningResultRepository, contextHasher, mock(TenantUserManagementService.class), mock(AuditEventPublisher.class), objectMapper);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalReasoningResult result = sampleReasoningResult(consultationId, patientId);
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        consultation.update("Fever", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalReasoningResultEntity entity = ClinicalReasoningResultEntity.create(
                tenantId,
                consultationId,
                patientId,
                1,
                "different-hash",
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                "MOCK",
                "mock-model",
                UUID.randomUUID(),
                "Amit Verma",
                result.generatedAt(),
                objectMapper.writeValueAsString(result)
        );
        when(reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(Optional.of(entity));
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(null);

        ClinicalReasoningResponse response = service.get(tenantId, consultationId);

        assertThat(response.reasoningStatus()).isEqualTo("STALE");
        assertThat(response.staleReason()).isEqualTo("Clinical data changed after this reasoning was generated. Regenerate to refresh.");
        assertThat(response.reasoningResult()).isNotNull();
    }

    @Test
    void marksReasoningStaleAfterPersistedConsultationChiefComplaintChanges() throws Exception {
        ClinicalReasoningResultRepository reasoningResultRepository = mock(ClinicalReasoningResultRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, mock(ClinicalReasoningEngine.class), reasoningResultRepository, contextHasher, mock(TenantUserManagementService.class), mock(AuditEventPublisher.class), objectMapper);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalReasoningResult result = sampleReasoningResult(consultationId, patientId);
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        consultation.update("Persistent cough and fatigue last 4 days", "Cough, sore throat, weakness", "Upper Respiratory Tract Infection", "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalReasoningRequest persistedRequest = persistedRequest(patientId);
        String currentHash = contextHasher.contextHash(tenantId, consultation, null, persistedRequest);
        ClinicalReasoningResultEntity entity = ClinicalReasoningResultEntity.create(
                tenantId,
                consultationId,
                patientId,
                1,
                currentHash,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                "MOCK",
                "mock-model",
                UUID.randomUUID(),
                "Amit Verma",
                result.generatedAt(),
                objectMapper.writeValueAsString(result)
        );
        when(reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(Optional.of(entity));
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(null);

        consultation.update("Persistent cough and fatigue with mild fever last 4 days", "Cough, sore throat, weakness", "Upper Respiratory Tract Infection", "CBC normal", null, null, null, null, null, null, null, null, null, null, null);

        ClinicalReasoningResponse response = service.get(tenantId, consultationId);

        assertThat(response.reasoningStatus()).isEqualTo("STALE");
        assertThat(response.staleReason()).isEqualTo("Clinical data changed after this reasoning was generated. Regenerate to refresh.");
        assertThat(response.reasoningResult()).isNotNull();
    }

    @Test
    void persistsNewReasoningVersionAndSupersedesPreviousVersion() throws Exception {
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ClinicalReasoningEngine clinicalReasoningEngine = mock(ClinicalReasoningEngine.class);
        ClinicalReasoningResultRepository reasoningResultRepository = mock(ClinicalReasoningResultRepository.class);
        ClinicalReasoningContextHasher contextHasher = new ClinicalReasoningContextHasher(new ClinicalReasoningPromptBuilder(), objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalReasoningService service = new ClinicalReasoningService(consultationRepository, clinicalContextService, clinicalReasoningEngine, reasoningResultRepository, contextHasher, tenantUserManagementService, auditEventPublisher, objectMapper);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorAppUserId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(tenantId, patientId, consultationId)).thenReturn(null);
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(new TenantUserRecord(actorAppUserId, tenantId, "sub", "doctor@example.com", "Dr Amit Verma", "ACTIVE", "DOCTOR", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now(), "PROVISIONED")));

        String currentHash = contextHasher.contextHash(tenantId, consultation, null, persistedRequest(patientId));

        ClinicalReasoningResult previousResult = sampleReasoningResult(consultationId, patientId);
        ClinicalReasoningResultEntity previous = ClinicalReasoningResultEntity.create(
                tenantId,
                consultation.getId(),
                patientId,
                1,
                currentHash,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                "MOCK",
                "mock-model",
                actorAppUserId,
                "Dr Amit Verma",
                previousResult.generatedAt(),
                objectMapper.writeValueAsString(previousResult)
        );
        when(reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultation.getId())).thenReturn(Optional.of(previous));
        when(reasoningResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalReasoningResult generatedResult = sampleReasoningResult(consultationId, patientId);
        when(clinicalReasoningEngine.generate(any(), any(), any(), any())).thenReturn(generatedResult);

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorAppUserId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        try {
            ClinicalReasoningResponse response = service.generate(tenantId, consultationId, persistedRequest(patientId), false);

            assertThat(response.reasoningStatus()).isEqualTo("CURRENT");
            assertThat(response.reasoningVersion()).isEqualTo(2);
            assertThat(response.generatedByDisplayName()).isEqualTo("Dr Amit Verma");
            verify(reasoningResultRepository, times(2)).save(any(ClinicalReasoningResultEntity.class));
            verify(reasoningResultRepository).save(previous);
        } finally {
            RequestContextHolder.clear();
        }
    }

    private ClinicalReasoningResult sampleReasoningResult(UUID consultationId, UUID patientId) {
        return new ClinicalReasoningResult(
                consultationId,
                patientId,
                OffsetDateTime.parse("2026-07-13T10:15:30Z"),
                "GEMINI",
                "gemini-2.5-flash",
                "HIGH",
                null,
                new com.deepthoughtnet.clinic.api.ai.reasoning.dto.DiagnosisCandidate("Viral Upper Respiratory Infection", BigDecimal.valueOf(0.82), "SUGGESTED", "Fever and cough", "No chest pain", List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Likely viral respiratory illness.",
                List.of(),
                List.of(),
                "Likely a viral illness.",
                new ClinicalReasoningResult.SourceContextSummary("Fever", List.of("Cough"), "INTAKE BP 136/86", List.of("Diabetes"), List.of("CBC normal"), List.of("Paracetamol")),
                new ReasoningMetadata("clinic.clinical.reasoning.engine.v1", "clinic.clinical.reasoning.v1", "v1", "v1", "GEMINI", "gemini-2.5-flash", Map.of(), "VALID", "req-1", "corr-1", 12L, false, false, "STOP", "COMPLETE", 321, "{\"ok\":true}", 321, null, "COMPLETE")
        );
    }

    private ClinicalReasoningRequest persistedRequest(UUID patientId) {
        return new ClinicalReasoningRequest(patientId, "Fever", "Fever and cough", "CBC normal", null, null, null, "CBC normal", null, null);
    }
}
