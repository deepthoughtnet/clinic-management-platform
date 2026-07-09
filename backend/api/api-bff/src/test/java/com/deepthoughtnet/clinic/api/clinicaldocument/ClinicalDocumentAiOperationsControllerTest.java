package com.deepthoughtnet.clinic.api.clinicaldocument;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentAiExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.security.ClinicalDocumentAiAuthorizationService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ClinicalDocumentAiOperationsControllerTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock private ClinicalDocumentService documentService;
    @Mock private ClinicalDocumentAiExtractionService aiExtractionService;
    @Mock private PatientService patientService;
    @Mock private ConsultationService consultationService;
    @Mock private PrescriptionService prescriptionService;
    @Mock private VaccinationService vaccinationService;
    @Mock private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    @Mock private ClinicalDocumentAiAuthorizationService clinicalDocumentAiAuthorizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClinicalDocumentController controller = new ClinicalDocumentController(
                documentService,
                aiExtractionService,
                patientService,
                consultationService,
                prescriptionService,
                vaccinationService,
                doctorAssignmentSecurityService,
                clinicalDocumentAiAuthorizationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), ACTOR_ID, "admin@jfcuat.local", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-doc-ai"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void clinicAdminCanRepairMemory() throws Exception {
        ClinicalMemoryRepairResult result = new ClinicalMemoryRepairResult(
                DOCUMENT_ID,
                "SUCCESS",
                OffsetDateTime.parse("2026-07-09T08:00:00Z"),
                ACTOR_ID,
                2,
                9,
                0,
                List.of(),
                3,
                "Memory repaired"
        );
        doNothing().when(clinicalDocumentAiAuthorizationService).requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID);
        when(aiExtractionService.repairClinicalMemory(TENANT_ID, DOCUMENT_ID, ACTOR_ID)).thenReturn(result);

        mockMvc.perform(post("/api/clinical-documents/{documentId}/clinical-memory/repair", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.insertedConceptCount").value(9));

        verify(clinicalDocumentAiAuthorizationService).requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID);
    }

    @Test
    void clinicAdminCanReprocessAi() throws Exception {
        doNothing().when(clinicalDocumentAiAuthorizationService).requireReprocessAccess(TENANT_ID, DOCUMENT_ID);
        when(aiExtractionService.reprocessExtraction(TENANT_ID, DOCUMENT_ID, ACTOR_ID)).thenReturn(org.mockito.Mockito.mock(ClinicalAiJobEntity.class));
        when(documentService.get(TENANT_ID, DOCUMENT_ID)).thenReturn(documentRecord());

        mockMvc.perform(post("/api/clinical-documents/{documentId}/ai/reprocess", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()));

        verify(clinicalDocumentAiAuthorizationService).requireReprocessAccess(TENANT_ID, DOCUMENT_ID);
    }

    @Test
    void forbiddenReasonIsReturnedFromRepairAuthorization() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("CLINIC_ADMIN is not authorized for document AI repair because selected tenant does not own this document"))
                .when(clinicalDocumentAiAuthorizationService).requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID);

        mockMvc.perform(post("/api/clinical-documents/{documentId}/clinical-memory/repair", DOCUMENT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CLINIC_ADMIN is not authorized for document AI repair because selected tenant does not own this document"));
    }

    private ClinicalDocumentRecord documentRecord() {
        return new ClinicalDocumentRecord(
                DOCUMENT_ID,
                TENANT_ID,
                PATIENT_ID,
                null,
                null,
                null,
                ACTOR_ID,
                "Admin User",
                ClinicalDocumentType.LAB_REPORT,
                "Report",
                null,
                null,
                "RECEPTION",
                "report.pdf",
                "application/pdf",
                100L,
                "checksum",
                "bucket",
                "storage-key",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                "QUEUED",
                "GEMINI",
                "gemini-1.5-flash",
                BigDecimal.valueOf(0.78),
                "Queued",
                "{}",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-07-09T08:05:00Z"),
                true,
                OffsetDateTime.parse("2026-07-09T08:05:00Z"),
                OffsetDateTime.parse("2026-07-09T08:05:00Z")
        );
    }
}
