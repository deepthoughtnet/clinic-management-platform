package com.deepthoughtnet.clinic.api.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionResponse;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiPrescriptionSuggestionEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiPrescriptionSuggestionRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultationAiPrescriptionSuggestionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getReturnsEmptyResponseWhenSuggestionIsAbsent() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        LabService labService = mock(LabService.class);
        ConsultationAiPrescriptionSuggestionRepository repository = mock(ConsultationAiPrescriptionSuggestionRepository.class);
        ConsultationAiPrescriptionSuggestionContextHasher contextHasher = new ConsultationAiPrescriptionSuggestionContextHasher(objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId));
        when(labService.listOrders(eq(tenantId), eq(consultationId), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());

        ConsultationAiPrescriptionSuggestionService service = new ConsultationAiPrescriptionSuggestionService(
                consultationService,
                clinicalContextService,
                labService,
                repository,
                contextHasher,
                tenantUserManagementService,
                auditEventPublisher,
                objectMapper
        );

        ConsultationAiPrescriptionSuggestionResponse response = service.get(tenantId, consultationId);

        assertThat(response.consultationId()).isEqualTo(consultationId.toString());
        assertThat(response.versionNumber()).isZero();
        assertThat(response.status()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.stale()).isFalse();
        assertThat(response.currentSourceHash()).isNotBlank();
    }

    @Test
    void saveCreatesNewVersionPersistsItemDecisionsAndSupersedesPrevious() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        LabService labService = mock(LabService.class);
        ConsultationAiPrescriptionSuggestionRepository repository = mock(ConsultationAiPrescriptionSuggestionRepository.class);
        ConsultationAiPrescriptionSuggestionContextHasher contextHasher = new ConsultationAiPrescriptionSuggestionContextHasher(objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-08-30T10:15:30Z");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "doctor@example.com", Set.of("DOCTOR"), "DOCTOR", "corr-ai-rx"));

        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId));
        when(labService.listOrders(eq(tenantId), eq(consultationId), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        when(tenantUserManagementService.list(eq(tenantId))).thenReturn(List.of(
                new TenantUserRecord(actorId, tenantId, "sub", "doctor@example.com", "Doctor Clinician", "ACTIVE", "DOCTOR", "ACTIVE", generatedAt, generatedAt, "ACTIVE")
        ));
        ConsultationAiPrescriptionSuggestionEntity previous = ConsultationAiPrescriptionSuggestionEntity.create(
                tenantId,
                consultationId,
                1,
                "legacy-source-hash",
                "OPENAI",
                "gpt-4o",
                actorId,
                "Doctor Clinician",
                OffsetDateTime.parse("2026-08-30T09:00:00Z"),
                suggestionPayloadJson(
                        "Pending prescription suggestion",
                        "AI draft",
                        false,
                        "OPENAI",
                        "gpt-4o",
                        "2026-08-30T09:00:00Z",
                        List.of(suggestionPayloadItem(
                                "item-1",
                                "Complete Blood Count",
                                null,
                                null,
                                null,
                                "Pending blood sample analysis",
                                null,
                                "Medicine: Complete Blood Count\nReason: Pending blood sample analysis",
                                "PENDING"
                        ))
                )
        );
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConsultationAiPrescriptionSuggestionService service = new ConsultationAiPrescriptionSuggestionService(
                consultationService,
                clinicalContextService,
                labService,
                repository,
                contextHasher,
                tenantUserManagementService,
                auditEventPublisher,
                objectMapper
        );

        ConsultationAiPrescriptionSuggestionRequest request = new ConsultationAiPrescriptionSuggestionRequest(
                "Pending prescription suggestion",
                "AI draft",
                false,
                "OPENAI",
                "gpt-4o",
                generatedAt,
                List.of(
                        new ConsultationAiPrescriptionSuggestionRequest.Item(
                                "item-1",
                                "Complete Blood Count",
                                null,
                                null,
                                null,
                                "Pending blood sample analysis",
                                null,
                                "Medicine: Complete Blood Count\nReason: Pending blood sample analysis",
                                "ACCEPTED"
                        ),
                        new ConsultationAiPrescriptionSuggestionRequest.Item(
                                "item-2",
                                "Paracetamol",
                                "500 mg",
                                "Twice daily",
                                "5 days",
                                "Fever relief",
                                "Review allergy history",
                                "Medicine: Paracetamol",
                                "EDITED"
                        )
                )
        );

        ConsultationAiPrescriptionSuggestionResponse response = service.save(tenantId, consultationId, request);

        ArgumentCaptor<ConsultationAiPrescriptionSuggestionEntity> captor = ArgumentCaptor.forClass(ConsultationAiPrescriptionSuggestionEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        ConsultationAiPrescriptionSuggestionEntity saved = captor.getAllValues().stream()
                .filter(entity -> entity.getVersionNumber() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getConsultationId()).isEqualTo(consultationId);
        assertThat(saved.getVersionNumber()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(ConsultationAiPrescriptionSuggestionEntity.ConsultationAiPrescriptionSuggestionStatus.CURRENT);
        assertThat(saved.getGeneratedByAppUserId()).isEqualTo(actorId);
        assertThat(saved.getGeneratedByDisplayName()).isEqualTo("Doctor Clinician");
        assertThat(saved.getSourceHash()).isNotBlank();
        assertThat(previous.getStatus()).isEqualTo(ConsultationAiPrescriptionSuggestionEntity.ConsultationAiPrescriptionSuggestionStatus.SUPERSEDED);
        assertThat(previous.getSupersededById()).isEqualTo(saved.getId());
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("CURRENT");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).status()).isEqualTo("ACCEPTED");
        assertThat(response.items().get(0).reviewedByDisplayName()).isEqualTo("Doctor Clinician");
        assertThat(response.items().get(0).reviewedAt()).isNotNull();
        assertThat(response.items().get(1).status()).isEqualTo("EDITED");
        assertThat(response.items().get(1).reviewedByDisplayName()).isEqualTo("Doctor Clinician");
        verify(auditEventPublisher).record(any());
    }

    @Test
    void saveIsIdempotentForMatchingGeneratedAtAndPayload() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        LabService labService = mock(LabService.class);
        ConsultationAiPrescriptionSuggestionRepository repository = mock(ConsultationAiPrescriptionSuggestionRepository.class);
        ConsultationAiPrescriptionSuggestionContextHasher contextHasher = new ConsultationAiPrescriptionSuggestionContextHasher(objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-08-30T10:15:30Z");
        AtomicReference<ConsultationAiPrescriptionSuggestionEntity> latestSuggestion = new AtomicReference<>();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "doctor@example.com", Set.of("DOCTOR"), "DOCTOR", "corr-ai-rx-2"));

        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId));
        when(labService.listOrders(eq(tenantId), eq(consultationId), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        when(tenantUserManagementService.list(eq(tenantId))).thenReturn(List.of(
                new TenantUserRecord(actorId, tenantId, "sub", "doctor@example.com", "Doctor Clinician", "ACTIVE", "DOCTOR", "ACTIVE", generatedAt, generatedAt, "ACTIVE")
        ));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId)))
                .thenAnswer(invocation -> Optional.ofNullable(latestSuggestion.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            ConsultationAiPrescriptionSuggestionEntity entity = invocation.getArgument(0);
            latestSuggestion.set(entity);
            return entity;
        });

        ConsultationAiPrescriptionSuggestionService service = new ConsultationAiPrescriptionSuggestionService(
                consultationService,
                clinicalContextService,
                labService,
                repository,
                contextHasher,
                tenantUserManagementService,
                auditEventPublisher,
                objectMapper
        );

        ConsultationAiPrescriptionSuggestionRequest request = new ConsultationAiPrescriptionSuggestionRequest(
                "Pending prescription suggestion",
                "AI draft",
                false,
                "OPENAI",
                "gpt-4o",
                generatedAt,
                List.of(new ConsultationAiPrescriptionSuggestionRequest.Item(
                        "item-1",
                        "Paracetamol",
                        "500 mg",
                        "Twice daily",
                        "5 days",
                        "Fever relief",
                        null,
                        "Medicine: Paracetamol\nDose: 500 mg",
                        "PENDING"
                ))
        );

        ConsultationAiPrescriptionSuggestionResponse created = service.save(tenantId, consultationId, request);
        ConsultationAiPrescriptionSuggestionResponse response = service.save(tenantId, consultationId, request);

        verify(repository, org.mockito.Mockito.times(1)).save(any());
        verify(auditEventPublisher).record(any());
        assertThat(created.versionNumber()).isEqualTo(1);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("CURRENT");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void getMarksSuggestionStaleWhenCurrentContextChanges() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        LabService labService = mock(LabService.class);
        ConsultationAiPrescriptionSuggestionRepository repository = mock(ConsultationAiPrescriptionSuggestionRepository.class);
        ConsultationAiPrescriptionSuggestionContextHasher contextHasher = new ConsultationAiPrescriptionSuggestionContextHasher(objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        ClinicalContextResponse context = sampleContext(tenantId, consultation.patientId(), consultationId);
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(context);
        when(labService.listOrders(eq(tenantId), eq(consultationId), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(
                ConsultationAiPrescriptionSuggestionEntity.create(
                        tenantId,
                        consultationId,
                        1,
                        "legacy-hash",
                        "OPENAI",
                        "gpt-4o",
                        UUID.randomUUID(),
                        "Doctor Clinician",
                        OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                        suggestionPayloadJson(
                                "Pending prescription suggestion",
                                "AI draft",
                                false,
                                "OPENAI",
                                "gpt-4o",
                                "2026-08-30T08:00:00Z",
                                List.of()
                        )
                )
        ));

        ConsultationAiPrescriptionSuggestionService service = new ConsultationAiPrescriptionSuggestionService(
                consultationService,
                clinicalContextService,
                labService,
                repository,
                contextHasher,
                tenantUserManagementService,
                auditEventPublisher,
                objectMapper
        );

        ConsultationAiPrescriptionSuggestionResponse response = service.get(tenantId, consultationId);

        assertThat(response.stale()).isTrue();
        assertThat(response.currentSourceHash()).isNotBlank();
        assertThat(response.sourceHash()).isEqualTo("legacy-hash");
    }

    @Test
    void getThrowsForUnknownConsultation() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        LabService labService = mock(LabService.class);
        ConsultationAiPrescriptionSuggestionRepository repository = mock(ConsultationAiPrescriptionSuggestionRepository.class);
        ConsultationAiPrescriptionSuggestionContextHasher contextHasher = new ConsultationAiPrescriptionSuggestionContextHasher(objectMapper);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ConsultationAiPrescriptionSuggestionService service = new ConsultationAiPrescriptionSuggestionService(
                consultationService,
                clinicalContextService,
                labService,
                repository,
                contextHasher,
                tenantUserManagementService,
                auditEventPublisher,
                objectMapper
        );

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(tenantId, consultationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consultation not found");
    }

    private ConsultationRecord consultationRecord(UUID tenantId, UUID consultationId) {
        return new ConsultationRecord(
                consultationId,
                tenantId,
                UUID.randomUUID(),
                "PAT-1",
                "Sample Patient",
                UUID.randomUUID(),
                "Doctor",
                UUID.randomUUID(),
                "Chief complaint",
                "Symptoms",
                "Diagnosis",
                "Notes",
                "Advice",
                LocalDate.of(2026, 8, 30),
                ConsultationStatus.DRAFT,
                120,
                80,
                72,
                36.8,
                null,
                70.0,
                172.0,
                98,
                18,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ClinicalContextResponse sampleContext(UUID tenantId, UUID patientId, UUID consultationId) {
        return new ClinicalContextResponse(
                tenantId,
                patientId,
                consultationId,
                new ClinicalContextResponse.PatientSnapshot("Sample Patient", 42, "MALE", null, null, List.of(), "2026-08-20"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of(), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary(null, List.of()),
                new ClinicalContextResponse.IntakeSummary(false, null, null, null, List.of(), null, null, null, null),
                new ClinicalContextResponse.LabIntelligence(null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), null),
                new ClinicalContextResponse.LongitudinalMemory(null, null, null, null, null, null, null, List.of(), List.of(), null),
                new ClinicalContextResponse.LongitudinalClinicalContext(List.of(), List.of(), null, List.of(), List.of()),
                "AI summary",
                "AI prompt",
                "{}",
                OffsetDateTime.parse("2026-08-30T08:00:00Z")
        );
    }

    private String suggestionPayloadJson(
            String summary,
            String rawText,
            boolean unstructured,
            String provider,
            String model,
            Object generatedAt,
            List<Map<String, Object>> items
    ) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("summary", summary);
            payload.put("rawText", rawText);
            payload.put("unstructured", unstructured);
            payload.put("provider", provider);
            payload.put("model", model);
            payload.put("generatedAt", generatedAt);
            payload.put("items", items);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build prescription suggestion payload", ex);
        }
    }

    private Map<String, Object> suggestionPayloadItem(
            String itemId,
            String medicine,
            String dose,
            String frequency,
            String duration,
            String reason,
            String safetyNote,
            String draftText,
            String status
    ) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("medicine", medicine);
        item.put("dose", dose);
        item.put("frequency", frequency);
        item.put("duration", duration);
        item.put("reason", reason);
        item.put("safetyNote", safetyNote);
        item.put("draftText", draftText);
        item.put("status", status);
        item.put("reviewedByAppUserId", null);
        item.put("reviewedByDisplayName", null);
        item.put("reviewedAt", null);
        return item;
    }
}
