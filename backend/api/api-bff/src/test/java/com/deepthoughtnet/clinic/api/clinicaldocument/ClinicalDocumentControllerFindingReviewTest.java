package com.deepthoughtnet.clinic.api.clinicaldocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalDocumentFindingReviewResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicalmemory.service.ClinicalDocumentFindingReviewService;
import com.deepthoughtnet.clinic.api.security.ClinicalDocumentAiAuthorizationService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClinicalDocumentControllerFindingReviewTest {
    @Mock private ClinicalDocumentService documentService;
    @Mock private com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentAiExtractionService aiExtractionService;
    @Mock private PatientService patientService;
    @Mock private ConsultationService consultationService;
    @Mock private PrescriptionService prescriptionService;
    @Mock private VaccinationService vaccinationService;
    @Mock private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    @Mock private ClinicalDocumentAiAuthorizationService clinicalDocumentAiAuthorizationService;
    @Mock private ClinicalDocumentFindingReviewService findingReviewService;

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
                doctorAssignmentSecurityService,
                clinicalDocumentAiAuthorizationService,
                findingReviewService
        );
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "doctor@example.com", null, null, "finding-review-test"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void findingReviewResponseIncludesCompletedReportMetadata() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID documentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ClinicalDocumentRecord document = new ClinicalDocumentRecord(
                documentId,
                tenantId,
                UUID.randomUUID(),
                null,
                null,
                null,
                UUID.randomUUID(),
                "Uploader",
                ClinicalDocumentType.LAB_REPORT,
                "CBC Report",
                null,
                LocalDate.of(2026, 8, 23),
                "DOCTOR",
                "cbc.pdf",
                "application/pdf",
                100L,
                "checksum",
                "bucket",
                "storage-key",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                "COMPLETE",
                "GEMINI",
                "gemini-1.5-flash",
                BigDecimal.valueOf(0.91),
                "Complete",
                "{}",
                null,
                null,
                null,
                reviewerId,
                OffsetDateTime.parse("2026-08-24T08:15:00Z"),
                null,
                true,
                OffsetDateTime.parse("2026-08-24T08:15:00Z"),
                OffsetDateTime.parse("2026-08-24T08:15:00Z")
        );
        when(documentService.get(tenantId, documentId)).thenReturn(document);
        when(documentService.resolveUserDisplayName(tenantId, reviewerId)).thenReturn("Dr. Completed Review");
        doNothing().when(clinicalDocumentAiAuthorizationService).requireFindingReviewAccess(tenantId, documentId);
        when(findingReviewService.list(tenantId, documentId)).thenReturn(List.of(
                new ClinicalDocumentFindingReviewService.FindingReviewRecord(
                        UUID.randomUUID(), "hba1c", "HbA1c", "5.9", "%", "4.0 - 5.6",
                        "5.9", "%", "4.0 - 5.6", "HIGH", "HbA1c 5.9 % 4.0 - 5.6 High",
                        "ACCEPTED", "CONFIRMED", reviewerId, OffsetDateTime.parse("2026-08-24T08:15:00Z"))
        ));

        ClinicalDocumentFindingReviewResponse response = controller.findingReview(documentId);

        assertThat(response.reviewCompleted()).isTrue();
        assertThat(response.reviewedBy()).isEqualTo(reviewerId.toString());
        assertThat(response.reviewedByDisplayName()).isEqualTo("Dr. Completed Review");
        assertThat(response.reviewedAt()).isEqualTo(OffsetDateTime.parse("2026-08-24T08:15:00Z"));
        assertThat(response.findings()).hasSize(1);
        assertThat(response.pendingCount()).isZero();
    }
}
