package com.deepthoughtnet.clinic.api.clinicaldocument;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentAiExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClinicalDocumentControllerDownloadTest {
    @Mock
    private ClinicalDocumentService documentService;

    @Mock
    private ClinicalDocumentAiExtractionService aiExtractionService;

    @Mock
    private PatientService patientService;

    @Mock
    private ConsultationService consultationService;

    @Mock
    private PrescriptionService prescriptionService;

    @Mock
    private VaccinationService vaccinationService;

    @Mock
    private DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    private ClinicalDocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new ClinicalDocumentController(
                documentService,
                aiExtractionService,
                patientService,
                consultationService,
                prescriptionService,
                vaccinationService,
                doctorAssignmentSecurityService
        );
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), null, null, null, "download-test"));
        doNothing().when(doctorAssignmentSecurityService).requirePatientAccess(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void viewStreamsPdfBytes() throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        byte[] bytes = new byte[] {37, 80, 68, 70, 45, 49, 46, 52};
        when(documentService.get(tenantId, documentId)).thenReturn(document(patientId, documentId));
        when(documentService.downloadBytes(tenantId, documentId)).thenReturn(bytes);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/patients/{patientId}/documents/{documentId}/view", patientId, documentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("LAB-3AF675E8B4-lab-report.pdf")))
                .andExpect(content().bytes(bytes));
    }

    private ClinicalDocumentRecord document(UUID patientId, UUID documentId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-05T06:00:00Z");
        return new ClinicalDocumentRecord(
                documentId,
                RequestContextHolder.requireTenantId(),
                patientId,
                null,
                "LABORATORY",
                "order-123",
                UUID.randomUUID(),
                "Doctor One",
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                "Published after verification",
                LocalDate.parse("2026-07-05"),
                "LABORATORY",
                "LAB-3AF675E8B4-lab-report.pdf",
                "application/pdf",
                8L,
                "checksum",
                "clinic-documents",
                "tenant/test/patients/test/documents/test/report.pdf",
                "PATIENT_VISIBLE",
                "PUBLISHED",
                "COMPLETED",
                "COMPLETED",
                null,
                null,
                null,
                BigDecimal.valueOf(0.99),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                now,
                now
        );
    }
}
