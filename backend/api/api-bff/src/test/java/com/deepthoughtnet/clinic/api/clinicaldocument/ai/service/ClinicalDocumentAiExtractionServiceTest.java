package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
import com.deepthoughtnet.clinic.ai.orchestration.service.AgentExecutionLogService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalDocumentAiExtractionServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Test
    void queueExtractionCreatesJobAndMarksDocumentQueued() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
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

        ClinicalAiJobEntity job = service.queueExtraction(TENANT_ID, DOCUMENT_ID, REVIEWER_ID);

        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.QUEUED);
        assertThat(document.getAiExtractionStatus()).isEqualTo("QUEUED");
        verify(auditEventPublisher).record(any());
    }

    @Test
    void processUpdatesDocumentAndMarksReviewRequired() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
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
        when(aiDoctorCopilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
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
        ));

        service.process(job.getId());

        assertThat(document.getAiExtractionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(job.getStatus()).isEqualTo(ClinicalAiJobStatus.REVIEW_REQUIRED);
        assertThat(job.getReviewStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(document.getAiExtractionProvider()).isEqualTo("GEMINI");
        assertThat(document.getAiExtractionConfidence()).isEqualByComparingTo("0.91");
        assertThat(document.getAiExtractionSummary()).contains("AI draft generated");
        assertThat(document.getAiExtractionStructuredJson()).contains("possibleAbnormalFindings");
        verify(agentExecutionLogService).record(eq(TENANT_ID), eq("CLINICAL_DOCUMENT_EXTRACTION"), eq(DOCUMENT_ID), anyString(), eq("REVIEW_REQUIRED"), isNull());
    }

    @Test
    void reviewApprovesAndAppendsPatientHistoryWhenRequested() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
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
                REVIEWER_ID,
                ClinicalDocumentType.LAB_REPORT,
                "report.pdf",
                "application/pdf",
                100L,
                "hash",
                "storage-key",
                "notes",
                null,
                null,
                null,
                "REVIEW_REQUIRED",
                "GEMINI",
                "gemini-1.5-flash",
                BigDecimal.valueOf(0.91),
                "AI draft generated.",
                "{\"hemoglobin\":\"10.2\"}",
                "review notes",
                null,
                null,
                REVIEWER_ID,
                OffsetDateTime.now(),
                "COMPLETED",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord()));
        doAnswer(invocation -> null).when(patientService).update(any(), any(), any(), any());

        service.review(TENANT_ID, DOCUMENT_ID, REVIEWER_ID, true, true, "Looks good", "{\"hemoglobin\":\"10.2\"}", "No override", "Edited summary");

        assertThat(document.getAiExtractionStatus()).isEqualTo("APPROVED");
        assertThat(document.getAiExtractionAcceptedJson()).isEqualTo("{\"hemoglobin\":\"10.2\"}");
        assertThat(document.getAiExtractionOverrideReason()).isEqualTo("No override");
        assertThat(document.getAiExtractionReviewNotes()).isEqualTo("Looks good");
        verify(patientService).update(any(), eq(PATIENT_ID), any(), eq(REVIEWER_ID));
        verify(auditEventPublisher).record(any());
    }

    @Test
    void processDoesNotCrashWhenPatientFieldsAreMissing() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
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
    }

    @Test
    void processMarksFailedWithoutThrowingWhenPatientContextIsMissing() {
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        ClinicalDocumentService documentService = mock(ClinicalDocumentService.class);
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        AiDoctorCopilotService aiDoctorCopilotService = mock(AiDoctorCopilotService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AgentExecutionLogService agentExecutionLogService = mock(AgentExecutionLogService.class);
        PatientService patientService = mock(PatientService.class);
        ClinicalDocumentAiExtractionService service = new ClinicalDocumentAiExtractionService(
                jobRepository,
                documentRepository,
                documentService,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
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
    }

    private ClinicalDocumentEntity document() {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                TENANT_ID,
                PATIENT_ID,
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

    private PatientRecord patientRecord() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
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
