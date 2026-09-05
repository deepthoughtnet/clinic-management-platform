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
import org.springframework.test.util.ReflectionTestUtils;

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
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT",
                "COMPLETED",
                """
                        Known diabetic
                        HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High
                        Estimated Average Glucose 194 mg/dL High
                        Random Blood Sugar 198 mg/dL 70 - 140 High
                        Total Cholesterol 228 mg/dL < 200 High
                        LDL Cholesterol 152 mg/dL < 100 High
                        HDL Cholesterol 39 mg/dL > 40 Low
                        Triglycerides 238 mg/dL < 150 High
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Clinical extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI draft generated.",
                        Map.ofEntries(
                                Map.entry("knownConditions", List.of("Diabetes Mellitus")),
                                Map.entry("answer", Map.of(
                                        "classification", Map.of(
                                                "diabetesMarkers", Map.of(
                                                        "details", List.of(
                                                                Map.of("test", "HbA1c", "result", "8.4 %", "referenceRange", "< 5.7 normal; > 6.5 diabetic", "flag", "HIGH"),
                                                                Map.of("test", "Estimated Average Glucose", "result", "194 mg/dL", "referenceRange", "", "flag", "HIGH"),
                                                                Map.of("test", "Random Blood Sugar", "result", "198 mg/dL", "referenceRange", "70 - 140", "flag", "HIGH")
                                                        )
                                                ),
                                                "lipidProfile", Map.of(
                                                        "details", List.of(
                                                                Map.of("test", "Total Cholesterol", "result", "228 mg/dL", "referenceRange", "< 200", "flag", "HIGH"),
                                                                Map.of("test", "LDL Cholesterol", "result", "152 mg/dL", "referenceRange", "< 100", "flag", "HIGH"),
                                                                Map.of("test", "HDL Cholesterol", "result", "39 mg/dL", "referenceRange", "> 40", "flag", "LOW"),
                                                                Map.of("test", "Triglycerides", "result", "238 mg/dL", "referenceRange", "< 150", "flag", "HIGH")
                                                        )
                                                )
                                        )
                                )),
                                Map.entry("summary", "Known diabetic with abnormal glycemic and lipid profile."),
                                Map.entry("recommendations", List.of("Review with clinician"))
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of("Verify values"),
                        List.of("Doctor review required"),
                        null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.REVIEW_REQUIRED);
        assertThat(job.getReviewStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(document.getAiExtractionProvider()).isEqualTo("GEMINI");
        assertThat(document.getAiExtractionConfidence()).isEqualByComparingTo("0.91");
        assertThat(document.getAiExtractionSummary()).contains("AI draft generated");
        assertThat(document.getAiExtractionStructuredJson()).contains("factualFindings");
        assertThat(document.getAiExtractionStructuredJson()).contains("labResults");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"estimated_average_glucose\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"cholesterol\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"ldl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hdl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"triglycerides\"");
        assertThat(RequestContextHolder.get()).isNull();
        verify(longitudinalMemoryService).ingestPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."));
        verify(agentExecutionLogService).record(eq(TENANT_ID), eq("CLINICAL_DOCUMENT_EXTRACTION"), eq(DOCUMENT_ID), anyString(), eq("REVIEW_REQUIRED"), eq(REVIEWER_ID));
    }

    @Test
    void processNormalizesOldAnswerClassificationSchemaIntoFactualLabResults() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\nEstimated Average Glucose 194 mg/dL\nRandom Blood Sugar 198 mg/dL 70 - 140 High\nTotal Cholesterol 228 mg/dL < 200 High\nLDL Cholesterol 152 mg/dL < 100 High\nHDL Cholesterol 39 mg/dL > 40 Low\nTriglycerides 238 mg/dL < 150 High"
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true, false, "Clinical extraction complete.", "GEMINI", "gemini-1.5-flash", "AI draft generated.",
                        Map.of("answer", Map.of("classification", Map.of(
                                "diabetesMarkers", Map.of("details", List.of(
                                        Map.of("test", "HbA1c", "result", "8.4 %", "referenceRange", "< 5.7 normal; > 6.5 diabetic", "flag", "HIGH"),
                                        Map.of("test", "Estimated Average Glucose", "result", "194 mg/dL", "referenceRange", "", "flag", "HIGH"),
                                        Map.of("test", "Random Blood Sugar", "result", "198 mg/dL", "referenceRange", "70 - 140", "flag", "HIGH")
                                )),
                                "lipidProfile", Map.of("details", List.of(
                                        Map.of("test", "Total Cholesterol", "result", "228 mg/dL", "referenceRange", "< 200", "flag", "HIGH"),
                                        Map.of("test", "LDL Cholesterol", "result", "152 mg/dL", "referenceRange", "< 100", "flag", "HIGH"),
                                        Map.of("test", "HDL Cholesterol", "result", "39 mg/dL", "referenceRange", "> 40", "flag", "LOW"),
                                        Map.of("test", "Triglycerides", "result", "238 mg/dL", "referenceRange", "< 150", "flag", "HIGH")
                                ))
                        ))),
                        BigDecimal.valueOf(0.91), List.of(), List.of(), null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"estimated_average_glucose\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"cholesterol\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"ldl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hdl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"triglycerides\"");
    }

    @Test
    void processPreservesNewFactualFindingsSchema() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\nEstimated Average Glucose 194 mg/dL\nRandom Blood Sugar 198 mg/dL 70 - 140 High\nTotal Cholesterol 228 mg/dL < 200 High\nLDL Cholesterol 152 mg/dL < 100 High\nHDL Cholesterol 39 mg/dL > 40 Low\nTriglycerides 238 mg/dL < 150 High"
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true, false, "Clinical extraction complete.", "GEMINI", "gemini-1.5-flash", "AI draft generated.",
                        Map.of(
                                "documentType", "EXTERNAL_LAB_REPORT",
                                "reportDate", "2026-01-08",
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("testName", "HbA1c", "canonicalKey", "hba1c", "value", "8.4", "unit", "%", "referenceRange", "< 5.7 normal; > 6.5 diabetic", "flag", "HIGH", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"),
                                                Map.of("testName", "Estimated Average Glucose", "canonicalKey", "estimated_average_glucose", "value", "194", "unit", "mg/dL", "referenceRange", "", "flag", "HIGH", "evidenceText", "Estimated Average Glucose 194 mg/dL High"),
                                                Map.of("testName", "Random Blood Sugar", "canonicalKey", "blood_sugar", "value", "198", "unit", "mg/dL", "referenceRange", "70 - 140", "flag", "HIGH", "evidenceText", "Random Blood Sugar 198 mg/dL 70 - 140 High")
                                        ),
                                        "conditions", List.of(Map.of("canonicalKey", "diabetes_mellitus", "label", "Diabetes Mellitus", "evidenceText", "Known diabetic")),
                                        "riskFlags", List.of(Map.of("canonicalKey", "diabetes_risk", "label", "Diabetes", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"))
                                ),
                                "summary", "Known diabetic with abnormal glycemic control.",
                                "recommendations", List.of(),
                                "limitations", List.of(),
                                "confidence", "HIGH"
                        ),
                        BigDecimal.valueOf(0.91), List.of(), List.of(), null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"factualFindings\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"estimated_average_glucose\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"confidence\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"answer\":");
    }

    @Test
    void processFallsBackToOcrLabLinesWhenAiReturnsNoLabRows() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\nEstimated Average Glucose 194 mg/dL\nRandom Blood Sugar 198 mg/dL 70 - 140 High\nTotal Cholesterol 228 mg/dL < 200 High\nLDL Cholesterol 152 mg/dL < 100 High\nHDL Cholesterol 39 mg/dL > 40 Low\nTriglycerides 238 mg/dL < 150 High\nHemoglobin 14.1 g/dL"
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true, false, "Clinical extraction complete.", "GEMINI", "gemini-1.5-flash", "AI draft generated.",
                        Map.of("answer", "Narrative only", "suggestedActions", List.of("Review with clinician")),
                        BigDecimal.valueOf(0.91), List.of(), List.of(), null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"estimated_average_glucose\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"cholesterol\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"ldl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hdl\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"triglycerides\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hemoglobin\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"canonicalKey\":\"hba1c\",\"value\":\"14.1\"");
    }

    @Test
    void processExtractsPipeDelimitedHbA1cTableRow() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Test | Result
                        HbA1c | 7.3 %
                        Estimated Average Glucose | 163 mg/dL
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true, false, "Clinical extraction complete.", "GEMINI", "gemini-1.5-flash", "AI draft generated.",
                        Map.of("answer", "Narrative only", "suggestedActions", List.of("Review with clinician")),
                        BigDecimal.valueOf(0.91), List.of(), List.of(), null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"7.3\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"estimated_average_glucose\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"163\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"mg/dL\"");
        verify(longitudinalMemoryService, atLeastOnce()).ingestPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), anyString());
    }

    @Test
    void processPrefersNestedAnswerLabResultsOverNarrativeJsonBlob() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Lipid Profile & HbA1c Report Date: 18 Mar 2025
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Clinical extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI draft generated.",
                        Map.of(
                                "answer", Map.of(
                                        "labResults", List.of(
                                                Map.of(
                                                        "testName", "HbA1c",
                                                        "result", "5.7",
                                                        "unit", "%",
                                                        "referenceRange", "4.0 - 5.6",
                                                        "flag", "High",
                                                        "evidenceText", "HbA1c 5.7 % 4.0 - 5.6 High"
                                                )
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\"}]}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"referenceRange\":\"4.0 - 5.6\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"evidenceText\":\"HbA1c 5.7 % 4.0 - 5.6 High\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"evidenceText\":\"{");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processIgnoresSerializedJsonBlobWhenNoStructuredProviderLabRowsArePresent() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Clinical extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI draft generated.",
                        Map.of(
                                "summary", "Narrative only",
                                "answer", "{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\",\"evidenceText\":\"HbA1c 5.7 % 4.0 - 5.6 High\"}]}"
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\",\"evidenceText\":\"HbA1c 5.7 % 4.0 - 5.6 High\"}]}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"canonicalKey\":\"hba1c\",\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"UNKNOWN\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"evidenceText\":\"{");
    }

    @Test
    void processResolvesAnswerExtractedClinicalDataLabResults() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Lipid Profile & HbA1c Report Date: 18 Mar 2025
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Clinical extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI draft generated.",
                        Map.of(
                                "answer", Map.of(
                                        "extractedClinicalData", Map.of(
                                                "labResults", List.of(
                                                        Map.of(
                                                                "testName", "HbA1c",
                                                                "result", "5.7",
                                                                "unit", "%",
                                                                "referenceRange", "4.0 - 5.6",
                                                                "flag", "High",
                                                                "evidenceText", "HbA1c 5.7 % 4.0 - 5.6 High"
                                                        ),
                                                        Map.of("testName", "Fasting Glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "High"),
                                                        Map.of("testName", "Total Cholesterol", "result", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "High"),
                                                        Map.of("testName", "LDL Cholesterol", "result", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "High"),
                                                        Map.of("testName", "HDL Cholesterol", "result", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "Normal"),
                                                        Map.of("testName", "Triglycerides", "result", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "Normal")
                                                )
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"answer\":{\"extractedClinicalData\":{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\"}]}}}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"referenceRange\":\"4.0 - 5.6\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processResolvesExtractedClinicalDataLabResultsAtRoot() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Clinical extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI draft generated.",
                        Map.of(
                                "extractedClinicalData", Map.of(
                                        "labResults", List.of(
                                                Map.of(
                                                        "testName", "HbA1c",
                                                        "result", "5.7",
                                                        "unit", "%",
                                                        "referenceRange", "4.0 - 5.6",
                                                        "flag", "High",
                                                        "evidenceText", "HbA1c 5.7 % 4.0 - 5.6 High"
                                                ),
                                                Map.of("testName", "Fasting Glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "High"),
                                                Map.of("testName", "Total Cholesterol", "result", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "High"),
                                                Map.of("testName", "LDL Cholesterol", "result", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "High"),
                                                Map.of("testName", "HDL Cholesterol", "result", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "Normal"),
                                                Map.of("testName", "Triglycerides", "result", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"extractedClinicalData\":{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\"}]}}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"referenceRange\":\"4.0 - 5.6\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processPrefersDeterministicOcrFactsOverPollutedAiLabRows() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                TENANT_ID, ClinicalAiJobType.DOCUMENT_EXTRACTION, "PATIENT_CLINICAL_DOCUMENT",
                DOCUMENT_ID, DOCUMENT_ID, PATIENT_ID, null, REVIEWER_ID, "{\"documentId\":\"doc\"}"
        );
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                "Hemoglobin 14.1 g/dL 13 - 17 Normal\nHbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\nRandom Blood Sugar 198 mg/dL 70 - 140 High\nTotal Cholesterol 228 mg/dL < 200 High\nLDL Cholesterol 152 mg/dL < 100 High\nHDL Cholesterol 39 mg/dL > 40 Low\nTriglycerides 238 mg/dL < 150 High"
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true, false, "Clinical extraction complete.", "GEMINI", "gemini-1.5-flash", "AI draft generated.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("testName", "HbA1c", "canonicalKey", "hba1c", "value", "14.1", "unit", "%", "evidenceText", "Review the elevated HbA1c and discuss abnormal lipid profile."),
                                                Map.of("testName", "Random Blood Sugar", "canonicalKey", "blood_sugar", "value", "1", "unit", "mg/dL", "evidenceText", "Recommend lifestyle modifications.")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.91), List.of(), List.of(), null
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"8.4\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"198\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Review the elevated HbA1c");
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
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                        DOCUMENT_ID,
                        "SUCCESS",
                        OffsetDateTime.now(),
                        REVIEWER_ID,
                        1,
                        1,
                        0,
                        List.of(),
                        0,
                        "Memory repaired"
                ));
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
                List.of("Doctor review required"),
                null
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
                List.of(),
                null
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
    void repairClinicalMemoryReusesStoredExtractionWithoutOCROrGemini() {
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
        setField(document, "aiExtractionStructuredJson", "{\"labs\":{\"hba1c\":\"8.4 % < 5.7 normal; > 6.5 diabetic High\"}}");
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", BigDecimal.valueOf(0.91));

        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.empty());
        when(documentService.get(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(new ClinicalDocumentRecord(
                DOCUMENT_ID,
                TENANT_ID,
                PATIENT_ID,
                null,
                null,
                null,
                REVIEWER_ID,
                "Reviewer",
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
                "{\"labs\":{\"hba1c\":\"8.4 % < 5.7 normal; > 6.5 diabetic High\"}}",
                "review notes",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                        DOCUMENT_ID,
                        "SUCCESS",
                        OffsetDateTime.now(),
                        REVIEWER_ID,
                        1,
                        1,
                        0,
                        List.of(),
                        0,
                        "Repair completed"
                ));

        service.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        verify(longitudinalMemoryService).repairPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."), eq(REVIEWER_ID));
        verify(textExtractionService, never()).extract(any(), any());
        verify(aiDoctorCopilotService, never()).draft(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void repairClinicalMemoryPreservesStoredKidneyFunctionLabsWithoutSourceText() {
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
        setField(document, "aiExtractionStructuredJson", """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"creatinine","testName":"Creatinine","value":"1.08","unit":"mg/dL"},
                      {"canonicalKey":"egfr","testName":"eGFR","value":"84","unit":"mL/min/1.73m²"}
                    ]
                  }
                }
                """);
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", BigDecimal.valueOf(0.18));

        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.empty());
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String structuredJson = invocation.getArgument(1);
                    String sourceText = invocation.getArgument(2);
                    assertThat(structuredJson).contains("creatinine").contains("egfr");
                    assertThat(sourceText).contains("Creatinine 1.08 mg/dL").contains("eGFR 84 mL/min/1.73m2");
                    return new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                            DOCUMENT_ID,
                            "SUCCESS",
                            OffsetDateTime.now(),
                            REVIEWER_ID,
                            1,
                            2,
                            0,
                            List.of(),
                            0,
                            "Memory repaired"
                    );
                });
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        verify(longitudinalMemoryService).repairPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.18)), eq("AI draft generated."), eq(REVIEWER_ID));
        verify(textExtractionService, never()).extract(any(), any());
        verify(aiDoctorCopilotService, never()).draft(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void repairClinicalMemoryIncludesPossibleAbnormalFindingsInRepairSourceText() {
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
        setField(document, "aiExtractionStructuredJson", """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"estimated_average_glucose","testName":"Estimated Average Glucose","value":"163","unit":"mg/dL","evidenceText":"Estimated Average Glucose 163 mg/dL"}
                    ]
                  },
                  "possibleAbnormalFindings":[
                    "Possible abnormal finding detected: HbA1c 7.3"
                  ]
                }
                """);
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", BigDecimal.valueOf(0.91));

        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.empty());
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String sourceText = invocation.getArgument(2);
                    assertThat(sourceText).contains("HbA1c 7.3");
                    assertThat(sourceText).contains("Estimated Average Glucose 163 mg/dL");
                    return new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                            DOCUMENT_ID,
                            "SUCCESS",
                            OffsetDateTime.now(),
                            REVIEWER_ID,
                            1,
                            2,
                            0,
                            List.of(),
                            0,
                            "Memory repaired"
                    );
                });
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        verify(longitudinalMemoryService).repairPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."), eq(REVIEWER_ID));
        verify(textExtractionService, never()).extract(any(), any());
        verify(aiDoctorCopilotService, never()).draft(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void repairClinicalMemoryPrefersStructuredExtractionAndEvidenceLinesOverAcceptedJson() {
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
        setField(document, "aiExtractionStructuredJson", "{\"labs\":{\"hba1c\":\"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\"}}");
        setField(document, "aiExtractionAcceptedJson", "{\"labs\":{\"hba1c\":\"1\"}}");
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", BigDecimal.valueOf(0.91));

        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.empty());
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), any(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String structuredJson = invocation.getArgument(1);
                    String sourceText = invocation.getArgument(2);
                    assertThat(structuredJson).contains("HbA1c 8.4");
                    assertThat(sourceText).contains("HbA1c 8.4");
                    return new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                            DOCUMENT_ID,
                            "SUCCESS",
                            OffsetDateTime.now(),
                            REVIEWER_ID,
                            1,
                            1,
                            0,
                            List.of(),
                            0,
                            "Memory repaired"
                    );
                });
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        verify(longitudinalMemoryService).repairPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."), eq(REVIEWER_ID));
        verify(aiDoctorCopilotService, never()).draft(any(), anyString(), anyString(), any(), any());
        verify(textExtractionService, never()).extract(any(), any());
    }

    @Test
    void repairClinicalMemoryFallsBackToJobResultBeforeAcceptedJson() {
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
        setField(document, "aiExtractionStructuredJson", null);
        setField(document, "aiExtractionAcceptedJson", "{\"labs\":{\"hba1c\":\"1\"}}");
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", BigDecimal.valueOf(0.91));
        ClinicalAiJobEntity latestJob = ClinicalAiJobEntity.queued(
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
        latestJob.markReviewRequired(
                "GEMINI",
                "gemini-1.5-flash",
                "PDFBOX",
                BigDecimal.valueOf(0.91),
                "AI draft generated.",
                "{\"labs\":{\"hba1c\":\"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High\"}}"
        );

        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(eq(TENANT_ID), eq(DOCUMENT_ID), eq(ClinicalAiJobType.DOCUMENT_EXTRACTION)))
                .thenReturn(Optional.of(latestJob));
        when(longitudinalMemoryService.repairPendingConcepts(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String structuredJson = invocation.getArgument(1);
                    String sourceText = invocation.getArgument(2);
                    assertThat(structuredJson).contains("HbA1c 8.4");
                    assertThat(structuredJson).doesNotContain("\"1\"");
                    assertThat(sourceText).contains("HbA1c 8.4");
                    return new com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult(
                            DOCUMENT_ID,
                            "SUCCESS",
                            OffsetDateTime.now(),
                            REVIEWER_ID,
                            1,
                            1,
                            0,
                            List.of(),
                            0,
                            "Memory repaired"
                    );
                });
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        verify(longitudinalMemoryService).repairPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.91)), eq("AI draft generated."), eq(REVIEWER_ID));
        verify(aiDoctorCopilotService, never()).draft(any(), anyString(), anyString(), any(), any());
        verify(textExtractionService, never()).extract(any(), any());
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
            return new AiDraftResponse(true, false, "ok", "GEMINI", "gemini-1.5-flash", "AI draft generated.", Map.of(), BigDecimal.valueOf(0.8), List.of(), List.of(), null);
        }).when(aiDoctorCopilotService).draft(any(), anyString(), anyString(), any(), any());

        service.process(jobA.getId());
        assertThat(RequestContextHolder.get()).isNull();
        service.process(jobB.getId());

        assertThat(invocationIndex.get()).isEqualTo(2);
        assertThat(RequestContextHolder.get()).isNull();
    }

    @Test
    void processMarksTruncatedExtractionIncompleteAndSkipsLongitudinalMemoryIngestion() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-truncated");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "AI response was truncated. Please retry.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "AI response was truncated. Please retry.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("testName", "Total Cholesterol", "canonicalKey", "cholesterol", "value", "206", "unit", "mg/dL", "flag", "HIGH", "evidenceText", "Total Cholesterol 206 mg/dL < 200 High"),
                                                Map.of("testName", "LDL Cholesterol", "canonicalKey", "ldl", "value", "132", "unit", "mg/dL", "flag", "HIGH", "evidenceText", "LDL Cholesterol 132 mg/dL < 100 High"),
                                                Map.of("testName", "HDL Cholesterol", "canonicalKey", "hdl", "value", "46", "unit", "mg/dL", "flag", "NORMAL", "evidenceText", "HDL Cholesterol 46 mg/dL > 40 Normal"),
                                                Map.of("testName", "Triglycerides", "canonicalKey", "triglycerides", "value", "139", "unit", "mg/dL", "flag", "NORMAL", "evidenceText", "Triglycerides 139 mg/dL < 150 Normal")
                                        )
                                ),
                                "confidence", "HIGH"
                        ),
                        BigDecimal.valueOf(0.91),
                        List.of(),
                        List.of(),
                        "MAX_TOKENS",
                        "TRUNCATED",
                        120,
                        "{\"partial\":true}",
                        "TRUNCATED"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionConfidence()).isEqualByComparingTo("0.35");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"INCOMPLETE\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"confidence\":\"LOW\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"blood_sugar\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("AI response was truncated. Review required.");
        verify(longitudinalMemoryService, never()).ingestPendingConcepts(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void processPersistsCompleteNormalCbcRowsWithoutFalseAbnormalFindings() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-cbc");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                        RBC 4.82 10^6/uL 4.5 - 5.9 Normal
                        WBC 6.8 10^3/uL 4.0 - 11.0 Normal
                        Platelets 248 10^3/uL 150 - 450 Normal
                        Neutrophils 58 % 40 - 70 Normal
                        Lymphocytes 33 % 20 - 40 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "CBC summary recognized.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "CBC summary recognized.",
                        Map.of("summary", "CBC summary recognized.", "confidence", "HIGH"),
                        BigDecimal.valueOf(0.82),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        240,
                        "{\"summary\":\"CBC\"}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionConfidence()).isEqualByComparingTo("0.82");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"confidence\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hemoglobin\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"rbc\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"wbc\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"platelets\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"neutrophils\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"lymphocytes\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"possibleAbnormalFindings\":[]");
        verify(longitudinalMemoryService).ingestPendingConcepts(eq(document), anyString(), anyString(), eq(BigDecimal.valueOf(0.82)), anyString());
    }

    @Test
    void processPersistsCbcCountAliasesFromProviderAndOcrWithoutDroppingRows() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-cbc-count");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                        RBC Count 4.82 10^6/uL 4.5 - 5.9 Normal
                        WBC Count 6.8 10^3/uL 4.0 - 11.0 Normal
                        Platelets 248 10^3/uL 150 - 450 Normal
                        Neutrophils 58 % 40 - 70 Normal
                        Lymphocytes 33 % 20 - 40 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "CBC summary recognized.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "CBC summary recognized.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("test", "Hemoglobin", "value", "14.1", "unit", "g/dL", "referenceRange", "13.0 - 17.0", "flag", "NORMAL", "evidenceText", "Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal"),
                                                Map.of("test", "RBC Count", "value", "4.82", "unit", "10^6/uL", "referenceRange", "4.5 - 5.9", "flag", "NORMAL", "evidenceText", "RBC Count 4.82 10^6/uL 4.5 - 5.9 Normal"),
                                                Map.of("test", "WBC Count", "value", "6.8", "unit", "10^3/uL", "referenceRange", "4.0 - 11.0", "flag", "NORMAL", "evidenceText", "WBC Count 6.8 10^3/uL 4.0 - 11.0 Normal"),
                                                Map.of("test", "Platelets", "value", "248", "unit", "10^3/uL", "referenceRange", "150 - 450", "flag", "NORMAL", "evidenceText", "Platelets 248 10^3/uL 150 - 450 Normal"),
                                                Map.of("test", "Neutrophils", "value", "58", "unit", "%", "referenceRange", "40 - 70", "flag", "NORMAL", "evidenceText", "Neutrophils 58 % 40 - 70 Normal"),
                                                Map.of("test", "Lymphocytes", "value", "33", "unit", "%", "referenceRange", "20 - 40", "flag", "NORMAL", "evidenceText", "Lymphocytes 33 % 20 - 40 Normal")
                                        )
                                ),
                                "summary", "CBC summary recognized.",
                                "confidence", "HIGH"
                        ),
                        BigDecimal.valueOf(0.82),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        240,
                        "{\"summary\":\"CBC\"}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"rbc\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"wbc\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
    }

    @Test
    void processPreservesHbA1cProviderRowWhenProviderUsesTestFieldAndReportAlsoContainsHemoglobin() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("test", "HbA1c", "value", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "HIGH", "evidenceText", "HbA1c 5.7 % 4.0 - 5.6 High"),
                                                Map.of("test", "Fasting Glucose", "value", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "HIGH", "evidenceText", "Fasting Glucose 102 mg/dL 70 - 99 High"),
                                                Map.of("test", "Total Cholesterol", "value", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "HIGH", "evidenceText", "Total Cholesterol 206 mg/dL < 200 High"),
                                                Map.of("test", "LDL Cholesterol", "value", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "HIGH", "evidenceText", "LDL Cholesterol 132 mg/dL < 100 High"),
                                                Map.of("test", "HDL Cholesterol", "value", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "NORMAL", "evidenceText", "HDL Cholesterol 46 mg/dL > 40 Normal"),
                                                Map.of("test", "Triglycerides", "value", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "NORMAL", "evidenceText", "Triglycerides 139 mg/dL < 150 Normal"),
                                                Map.of("test", "Hemoglobin", "value", "14.1", "unit", "g/dL", "referenceRange", "13.0 - 17.0", "flag", "NORMAL", "evidenceText", "Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hemoglobin\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"14.1\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processPreservesExactLiveHbA1cProviderRowWithoutExplicitEvidenceText() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-live-row");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("testName", "HbA1c", "result", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "High"),
                                                Map.of("testName", "Fasting Glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "High"),
                                                Map.of("testName", "Total Cholesterol", "result", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "High"),
                                                Map.of("testName", "LDL Cholesterol", "result", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "High"),
                                                Map.of("testName", "HDL Cholesterol", "result", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "Normal"),
                                                Map.of("testName", "Triglycerides", "result", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"referenceRange\":\"4.0 - 5.6\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"mapped\":true");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("\"canonicalKey\":\"diabetes_risk\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
    }

    @Test
    void canonicalLabKeyPreservesHbA1cDigitsAndAliases() {
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                mock(ClinicalAiJobRepository.class),
                mock(ClinicalDocumentRepository.class),
                mock(ClinicalDocumentService.class),
                mock(PatientLongitudinalMemoryService.class),
                mock(AppUserRepository.class),
                mock(ClinicalDocumentTextExtractionService.class),
                mock(AiDoctorCopilotService.class),
                mock(ObjectStorageService.class),
                mock(AuditEventPublisher.class),
                mock(AgentExecutionLogService.class),
                mock(PatientService.class),
                mock(TenantNotificationSettingsService.class),
                new ObjectMapper(),
                1000L,
                3
        );

        assertThat((String) ReflectionTestUtils.invokeMethod(service, "slug", "HbA1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "HbA1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "HBA1C")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Hb A1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Hemoglobin A1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Glycated Hemoglobin")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Glycosylated Hemoglobin")).isEqualTo("hba1c");
    }

    @Test
    void canonicalLabKeyDoesNotCollapseCompoundAnalytes() {
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                mock(ClinicalAiJobRepository.class),
                mock(ClinicalDocumentRepository.class),
                mock(ClinicalDocumentService.class),
                mock(PatientLongitudinalMemoryService.class),
                mock(AppUserRepository.class),
                mock(ClinicalDocumentTextExtractionService.class),
                mock(AiDoctorCopilotService.class),
                mock(ObjectStorageService.class),
                mock(AuditEventPublisher.class),
                mock(AgentExecutionLogService.class),
                mock(PatientService.class),
                mock(TenantNotificationSettingsService.class),
                new ObjectMapper(),
                1000L,
                3
        );

        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Hemoglobin")).isEqualTo("hemoglobin");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Reticulocyte Hemoglobin Equivalent (RET-He)")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Platelets")).isEqualTo("platelets");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Immature Platelet Fraction (IPF)")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "canonicalLabKey", "Glucose-6-Phosphate")).isNull();
    }

    @Test
    void processPreservesHbA1cProviderRowsAcrossSupportedNameFieldsAndAliases() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-fields");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("testName", "Hemoglobin A1c", "value", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "HIGH", "evidenceText", "HbA1c 5.7 % 4.0 - 5.6 High"),
                                                Map.of("label", "Fasting Glucose", "value", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "HIGH", "evidenceText", "Fasting Glucose 102 mg/dL 70 - 99 High"),
                                                Map.of("test", "Total Cholesterol", "value", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "HIGH", "evidenceText", "Total Cholesterol 206 mg/dL < 200 High"),
                                                Map.of("test", "LDL Cholesterol", "value", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "HIGH", "evidenceText", "LDL Cholesterol 132 mg/dL < 100 High"),
                                                Map.of("test", "HDL Cholesterol", "value", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "NORMAL", "evidenceText", "HDL Cholesterol 46 mg/dL > 40 Normal"),
                                                Map.of("test", "Triglycerides", "value", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "NORMAL", "evidenceText", "Triglycerides 139 mg/dL < 150 Normal"),
                                                Map.of("testName", "Hemoglobin", "value", "14.1", "unit", "g/dL", "referenceRange", "13.0 - 17.0", "flag", "NORMAL", "evidenceText", "Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hemoglobin\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processPreservesHbA1cProviderRowsAcrossNameAnalyteAndParameterFields() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-name-variants");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("name", "Hemoglobin A1c", "result", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "High"),
                                                Map.of("analyte", "Fasting Glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "High"),
                                                Map.of("parameter", "Total Cholesterol", "result", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "High"),
                                                Map.of("name", "LDL Cholesterol", "result", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "High"),
                                                Map.of("analyte", "HDL Cholesterol", "result", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "Normal"),
                                                Map.of("parameter", "Triglycerides", "result", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
    }

    @Test
    void processPreservesHbA1cAliasesAcrossLabelTextCaseVariants() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-label-case");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        HbA1c 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("label", "HBA1C", "result", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "high"),
                                                Map.of("label", "fasting glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "HIGH"),
                                                Map.of("label", "TOTAL CHOLESTEROL", "result", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "High"),
                                                Map.of("label", "ldl cholesterol", "result", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "HIGH"),
                                                Map.of("label", "HDL CHOLESTEROL", "result", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "normal"),
                                                Map.of("label", "Triglycerides", "result", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"flag\":\"HIGH\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
    }

    @Test
    void processPreservesHbA1cAliasesFromLabelField() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-aliases");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Glycated Hemoglobin 5.7 % 4.0 - 5.6 High
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Lipid and HbA1c extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Lipid and HbA1c extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("label", "Glycated Hemoglobin", "value", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "HIGH", "evidenceText", "Glycated Hemoglobin 5.7 % 4.0 - 5.6 High"),
                                                Map.of("label", "Fasting Glucose", "value", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "HIGH", "evidenceText", "Fasting Glucose 102 mg/dL 70 - 99 High"),
                                                Map.of("label", "Total Cholesterol", "value", "206", "unit", "mg/dL", "referenceRange", "< 200", "flag", "HIGH", "evidenceText", "Total Cholesterol 206 mg/dL < 200 High"),
                                                Map.of("label", "LDL Cholesterol", "value", "132", "unit", "mg/dL", "referenceRange", "< 100", "flag", "HIGH", "evidenceText", "LDL Cholesterol 132 mg/dL < 100 High"),
                                                Map.of("label", "HDL Cholesterol", "value", "46", "unit", "mg/dL", "referenceRange", "> 40", "flag", "NORMAL", "evidenceText", "HDL Cholesterol 46 mg/dL > 40 Normal"),
                                                Map.of("label", "Triglycerides", "value", "139", "unit", "mg/dL", "referenceRange", "< 150", "flag", "NORMAL", "evidenceText", "Triglycerides 139 mg/dL < 150 Normal")
                                        )
                                )
                        ),
                        BigDecimal.valueOf(0.9),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        320,
                        "{\"ok\":true}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"Glycated Hemoglobin\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
    }

    @Test
    void processRecoversHbA1cFromNarrativeProviderSummaryWhenStructuredLabArrayIsMissing() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-hba1c-narrative");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                """
                        Fasting Glucose 102 mg/dL 70 - 99 High
                        Total Cholesterol 206 mg/dL < 200 High
                        LDL Cholesterol 132 mg/dL < 100 High
                        HDL Cholesterol 46 mg/dL > 40 Normal
                        Triglycerides 139 mg/dL < 150 Normal
                        """
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "HbA1c: 5.7% (High, reference range 4.0 - 5.6%)",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "HbA1c: 5.7% (High, reference range 4.0 - 5.6%)",
                        Map.of("summary", "HbA1c: 5.7% (High, reference range 4.0 - 5.6%)", "confidence", "HIGH"),
                        BigDecimal.valueOf(0.82),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        240,
                        "HbA1c: 5.7% (High, reference range 4.0 - 5.6%)",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"hba1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"HbA1c\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"value\":\"5.7\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"unit\":\"%\"");
        assertThat(document.getAiExtractionStructuredJson()).doesNotContain("Some detected lab rows were not extracted into structured results: hba1c");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"extractionStatus\":\"COMPLETE\"");
    }

    @Test
    void processPreservesUnknownButValidProviderLabRowsAsUnmappedFacts() {
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
                jobRepository, documentRepository, documentService, longitudinalMemoryService, appUserRepository,
                textExtractionService, aiDoctorCopilotService, storageService, auditEventPublisher,
                agentExecutionLogService, patientService, notificationSettingsService, new ObjectMapper(), 1000L, 3
        );

        ClinicalDocumentEntity document = document();
        ClinicalAiJobEntity job = queuedJob(TENANT_ID, DOCUMENT_ID, PATIENT_ID, REVIEWER_ID, "corr-unmapped");
        when(jobRepository.findById(eq(job.getId()))).thenReturn(Optional.of(job));
        when(documentRepository.findByTenantIdAndId(eq(TENANT_ID), eq(DOCUMENT_ID))).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getObjectBytes(anyString())).thenReturn("fake-bytes".getBytes());
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "TESSERACT", "COMPLETED",
                "Vitamin XYZ Marker 7.8 ng/mL 5.0 - 10.0 Normal"
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(
                new AiDraftResponse(
                        true,
                        false,
                        "Structured extraction complete.",
                        "GEMINI",
                        "gemini-1.5-flash",
                        "Structured extraction complete.",
                        Map.of(
                                "factualFindings", Map.of(
                                        "labResults", List.of(
                                                Map.of("test", "Vitamin XYZ Marker", "value", "7.8", "unit", "ng/mL", "referenceRange", "5.0 - 10.0", "flag", "NORMAL", "evidenceText", "Vitamin XYZ Marker 7.8 ng/mL 5.0 - 10.0 Normal")
                                        )
                                ),
                                "confidence", "HIGH"
                        ),
                        BigDecimal.valueOf(0.8),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        120,
                        "{\"summary\":\"ok\"}",
                        "VALID"
                )
        );

        service.process(job.getId());

        assertThat(document.getAiExtractionStructuredJson()).contains("\"canonicalKey\":\"unmapped_vitamin_xyz_marker\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"testName\":\"Vitamin XYZ Marker\"");
        assertThat(document.getAiExtractionStructuredJson()).contains("\"mapped\":false");
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

    private static void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
