package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiDoctorCopilotService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicalmemory.service.PatientLongitudinalMemoryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AgentExecutionLogService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClinicalDocumentAiExtractionServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void queueExtractionCreatesJobAndMarksDocumentQueued() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByTenantIdAndId(eq(TENANT_ID), eq(REVIEWER_ID))).thenReturn(Optional.of(appUser("reviewer.user")));
        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), REVIEWER_ID, "sub", java.util.Set.of("DOCTOR"), "DOCTOR", "corr-123"));

        ClinicalAiJobEntity job = service.queueExtraction(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.QUEUED);
        assertThat(document.getAiExtractionStatus()).isEqualTo("QUEUED");
        assertThat(job.getRequestJson()).contains("\"tenantId\":\"" + TENANT_ID + "\"");
        assertThat(job.getRequestJson()).contains("\"actorUsername\":\"reviewer.user\"");
        assertThat(job.getRequestJson()).contains("\"correlationId\":\"corr-123\"");
        verify(auditEventPublisher).record(any());
    }

    @Test
    void reprocessExtractionCreatesFreshJobForSameDocument() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByTenantIdAndId(eq(TENANT_ID), eq(REVIEWER_ID))).thenReturn(Optional.of(appUser("reviewer.user")));
        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), REVIEWER_ID, "sub", java.util.Set.of("DOCTOR"), "DOCTOR", "corr-456"));

        ClinicalAiJobEntity job = service.reprocessExtraction(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.QUEUED);
        assertThat(job.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(job.getDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(job.getRequestJson()).contains("\"reprocess\":true");
        verify(jobRepository, never()).findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(any(), any(), any());
        verify(auditEventPublisher).record(any());
    }

    @Test
    void processUpdatesDocumentAndMarksReviewRequired() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID,
                DOCUMENT_ID,
                PATIENT_ID,
                null,
                REVIEWER_ID,
                "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult("TESSERACT", "COMPLETED", "Hemoglobin 10.2 g/dL\nGlucose 210 mg/dL"));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        doAnswer(invocation -> {
            assertThat(RequestContextHolder.requireTenantId()).isEqualTo(TENANT_ID);
            assertThat(RequestContextHolder.require().appUserId()).isEqualTo(REVIEWER_ID);
            assertThat(RequestContextHolder.require().correlationId()).isNotBlank();
            return new AiDraftResponse(
                    true,
                    false,
                    "Clinical extraction complete.",
                    "GEMINI",
                    "gemini-1.5-flash",
                    "AI draft generated.",
                    Map.of("hemoglobin", "10.2", "glucose", "210"),
                    BigDecimal.valueOf(0.91),
                    List.of("Verify values"),
                    List.of("Doctor review required")
            );
        }).when(aiDoctorCopilotService).draft(any(), anyString(), anyString(), any(), any());

        service.process(job.getId());

        assertThat(document.getAiExtractionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.REVIEW_REQUIRED);
        assertThat(job.getReviewStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(document.getAiExtractionProvider()).isEqualTo("GEMINI");
        assertThat(document.getAiExtractionConfidence()).isEqualByComparingTo("0.91");
        assertThat(document.getAiExtractionSummary()).contains("AI draft generated");
        assertThat(document.getAiExtractionStructuredJson()).contains("possibleAbnormalFindings");
        assertThat(RequestContextHolder.get()).isNull();
        verify(longitudinalMemoryService).ingestPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."));
        verify(agentExecutionLogService).record(eq(TENANT_ID), eq("CLINICAL_DOCUMENT_EXTRACTION"), eq(DOCUMENT_ID), anyString(), eq("REVIEW_REQUIRED"), eq(REVIEWER_ID));
    }

    @Test
    void processKeepsAiExtractionVisibleWhenConceptPersistenceFails() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID,
                DOCUMENT_ID,
                PATIENT_ID,
                null,
                REVIEWER_ID,
                "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult("TESSERACT", "COMPLETED", "Hemoglobin 10.2 g/dL\nGlucose 210 mg/dL"));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        doAnswer(invocation -> new AiDraftResponse(
                true,
                false,
                "Clinical extraction complete.",
                "GEMINI",
                "gemini-1.5-flash",
                "AI draft generated.",
                Map.of("hemoglobin", "10.2", "glucose", "210"),
                BigDecimal.valueOf(0.91),
                List.of("Verify values"),
                List.of("Doctor review required")
        )).when(aiDoctorCopilotService).draft(any(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            throw new RuntimeException("Concept persistence failed");
        }).when(longitudinalMemoryService).ingestPendingConcepts(any(), anyString(), anyString(), any(), anyString());

        service.process(job.getId());

        assertThat(document.getAiExtractionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(document.getAiExtractionSummary()).contains("AI draft generated");
        verify(documentRepository, atLeastOnce()).save(any());
        verify(jobRepository, atLeastOnce()).save(any());
    }

    @Test
    void reviewApprovesAndAppendsPatientHistoryWhenRequested() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentService.get(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(new ClinicalDocumentRecord(
                DOCUMENT_ID,
                TENANT_ID,
                PATIENT_ID,
                null,
                null,
                null,
                REVIEWER_ID,
                "Doctor Reviewer",
                ClinicalDocumentType.LAB_REPORT,
                "report.pdf",
                "notes",
                null,
                "LABORATORY",
                "report.pdf",
                "application/pdf",
                100L,
                "hash",
                "clinic-documents",
                "storage-key",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                "REVIEW_REQUIRED",
                "GEMINI",
                "gemini-1.5-flash",
                BigDecimal.valueOf(0.91),
                "AI draft generated.",
                "{\"hemoglobin\":\"10.2\"}",
                "review notes",
                null,
                null,
                null,
                OffsetDateTime.now(),
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        doAnswer(invocation -> null).when(patientService).update(any(), any(), any(), any(), any(), any(), any());

        service.review(TENANT_ID, DOCUMENT_ID, REVIEWER_ID, true, true, "Looks good", "{\"hemoglobin\":\"10.2\"}", "No override", "Edited summary");

        assertThat(document.getAiExtractionStatus()).isEqualTo("APPROVED");
        assertThat(document.getAiExtractionAcceptedJson()).isEqualTo("{\"hemoglobin\":\"10.2\"}");
        assertThat(document.getAiExtractionOverrideReason()).isEqualTo("No override");
        assertThat(document.getAiExtractionReviewNotes()).isEqualTo("Looks good");
        verify(patientService, atLeastOnce()).update(any(), any(), any(), any(), any(), any(), any());
        verify(auditEventPublisher).record(any());
    }

    @Test
    void processDoesNotCrashWhenPatientFieldsAreMissing() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID,
                DOCUMENT_ID,
                PATIENT_ID,
                null,
                REVIEWER_ID,
                "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult("TESSERACT", "COMPLETED", "Hemoglobin 10.2 g/dL"));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecordWithNulls()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Clinical extraction complete.",
                "GEMINI",
                "gemini-1.5-flash",
                "AI draft generated.",
                Map.of(),
                BigDecimal.valueOf(0.91),
                List.of(),
                List.of()
        ));

        service.process(job.getId());

        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.REVIEW_REQUIRED);
        verify(longitudinalMemoryService).ingestPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."));
    }

    @Test
    void processMarksFailedWithoutThrowingWhenPatientContextIsMissing() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        ClinicalDocumentEntity document = documentWithNullPatient();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID,
                DOCUMENT_ID,
                null,
                null,
                REVIEWER_ID,
                "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult("TESSERACT", "COMPLETED", "Hemoglobin 10.2 g/dL"));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenThrow(new IllegalStateException("provider down"));

        service.process(job.getId());

        assertThat(job.getStatus()).isIn(ClinicalAiJobStatus.RETRY_SCHEDULED, ClinicalAiJobStatus.FAILED);
        assertThat(document.getAiExtractionSummary()).isEqualTo("AI processing could not complete. Please retry.");
        assertThat(document.getAiExtractionSummary()).doesNotContain("provider down");
        assertThat(RequestContextHolder.get()).isNull();
    }

    @Test
    void processDoesNotLeakTenantContextAcrossJobs() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        TenantNotificationSettingsService notificationSettingsService = mock(TenantNotificationSettingsService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new ObjectMapper(),
                1000L,
                3
        );

        UUID tenantB = UUID.randomUUID();
        UUID documentBId = UUID.randomUUID();
        UUID patientBId = UUID.randomUUID();
        UUID reviewerB = UUID.randomUUID();
        ClinicalDocumentEntity documentA = document();
        ClinicalDocumentEntity documentB = document(tenantB, patientBId, documentBId, reviewerB);
        ClinicalAiJobEntity jobA = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-A");
        ClinicalAiJobEntity jobB = queuedJob(tenantB, documentBId, patientBId, reviewerB, "corr-B");

        when(jobRepository.findById(eq(jobA.getId()))).thenReturn(Optional.of(jobA));
        when(jobRepository.findById(eq(jobB.getId()))).thenReturn(Optional.of(jobB));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(documentA));
        when(documentRepository.findByTenantIdAndId(eq(tenantB), eq(documentBId))).thenReturn(Optional.of(documentB));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult("TESSERACT", "COMPLETED", "Hemoglobin 10.2 g/dL"));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(patientService.findById(eq(tenantB), eq(patientBId))).thenReturn(Optional.of(patientRecord(tenantB, patientBId)));
        java.util.concurrent.atomic.AtomicInteger invocationIndex = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            int index = invocationIndex.getAndIncrement();
            if (index == 0) {
                assertThat(RequestContextHolder.requireTenantId()).isEqualTo(TENANT_ID);
                assertThat(RequestContextHolder.require().appUserId()).isEqualTo(REVIEWER_ID);
                assertThat(RequestContextHolder.require().correlationId()).isEqualTo("corr-A");
            } else {
                assertThat(RequestContextHolder.requireTenantId()).isEqualTo(tenantB);
                assertThat(RequestContextHolder.require().appUserId()).isEqualTo(reviewerB);
                assertThat(RequestContextHolder.require().correlationId()).isEqualTo("corr-B");
            }
            return new AiDraftResponse(true, false, "ok", "GEMINI", "gemini-1.5-flash", "AI draft generated.", Map.of(), BigDecimal.valueOf(0.8), List.of(), List.of());
        }).when(aiDoctorCopilotService).draft(any(), anyString(), anyString(), any(), any());

        service.process(jobA.getId());
        assertThat(RequestContextHolder.get()).isNull();
        service.process(jobB.getId());

        assertThat(invocationIndex.get()).isEqualTo(2);
        assertThat(RequestContextHolder.get()).isNull();
    }

    private ClinicalDocumentEntity document() {
        return document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, REVIEWER_ID);
    }

    private ClinicalDocumentEntity document(UUID tenantId, UUID patientId, UUID documentId, UUID reviewerId) {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                tenantId,
                patientId,
                null,
                null,
                reviewerId,
                ClinicalDocumentType.LAB_REPORT,
                "report.pdf",
                "application/pdf",
                100,
                "hash",
                "storage-key",
                "notes",
                null,
                null,
                null
        );
        try {
            Field idField = ClinicalDocumentEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, documentId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }

    private PatientRecord patientRecord() {
        return patientRecord(TENANT_ID, PATIENT_ID);
    }

    private PatientRecord patientRecord(UUID tenantId, UUID patientId) {
        return new PatientRecord(
                patientId,
                tenantId,
                "PAT-001",
                "Raj",
                "Sharma",
                PatientGender.MALE,
                LocalDate.of(1984, 1, 1),
                40,
                "9876543210",
                "patient@clinic.local",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Peanuts",
                "Diabetes",
                "Metformin",
                "Appendectomy",
                "Old notes",
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ClinicalAiJobEntity queuedJob(UUID tenantId, UUID documentId, UUID patientId, UUID reviewerId, String correlationId) {
        return ClinicalAiJobEntity.queued(
                tenantId,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                documentId,
                documentId,
                patientId,
                null,
                reviewerId,
                "{\"context\":{\"tenantId\":\"%s\",\"actorAppUserId\":\"%s\",\"correlationId\":\"%s\",\"requestId\":\"%s\",\"patientId\":\"%s\",\"documentId\":\"%s\"}}"
                        .formatted(tenantId, reviewerId, correlationId, correlationId, patientId, documentId)
        );
    }

    private AppUserEntity appUser(String username) {
        AppUserEntity user = AppUserEntity.create(TENANT_ID, "sub", username + "@clinic.local", "Reviewer");
        user.updateIdentity(username, null);
        return user;
    }

    private PatientRecord patientRecordWithNulls() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                null,
                "Raj",
                "Sharma",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ClinicalDocumentEntity documentWithNullPatient() {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                TENANT_ID,
                null,
                null,
                null,
                REVIEWER_ID,
                ClinicalDocumentType.LAB_REPORT,
                "report.pdf",
                "application/pdf",
                100,
                "hash",
                "storage-key",
                "notes",
                null,
                null,
                null
        );
        try {
            Field idField = ClinicalDocumentEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, DOCUMENT_ID);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
