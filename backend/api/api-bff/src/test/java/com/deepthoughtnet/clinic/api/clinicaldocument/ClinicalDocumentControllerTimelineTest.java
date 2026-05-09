package com.deepthoughtnet.clinic.api.clinicaldocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentAiExtractionService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
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
class ClinicalDocumentControllerTimelineTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

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
                doctorAssignmentSecurityService
        );
        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), ACTOR_ID, null, null, null, "timeline-test"));
        doNothing().when(doctorAssignmentSecurityService).requirePatientAccess(any(), any());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void patientTimelineCombinesDocumentsConsultationsAndPrescriptions() {
        when(patientService.findById(TENANT_ID, PATIENT_ID)).thenReturn(java.util.Optional.of(patient()));
        when(documentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(
                document(OffsetDateTime.parse("2026-05-07T08:15:00Z"), "xray.png", ClinicalDocumentType.X_RAY, "Dr. Rao", "Metro Imaging"),
                document(OffsetDateTime.parse("2026-05-08T09:30:00Z"), "lab.pdf", ClinicalDocumentType.LAB_REPORT, null, null)
        ));
        when(consultationService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(
                consultation(OffsetDateTime.parse("2026-05-08T10:00:00Z"), "Acute bronchitis", ConsultationStatus.COMPLETED, LocalDate.of(2026, 5, 15))
        ));
        when(prescriptionService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(
                prescription(OffsetDateTime.parse("2026-05-08T11:15:00Z"), 2, PrescriptionStatus.FINALIZED, "FOLLOW_UP", "Same-day correction", LocalDate.of(2026, 5, 16)),
                prescription(OffsetDateTime.parse("2026-05-07T07:45:00Z"), 1, PrescriptionStatus.SUPERSEDED, null, null, LocalDate.of(2026, 5, 10))
        ));

        List<com.deepthoughtnet.clinic.api.clinicaldocument.dto.PatientTimelineItemResponse> timeline = controller.patientTimeline(PATIENT_ID);

        assertThat(timeline).hasSize(5);
        assertThat(timeline.get(0).itemType()).isEqualTo("PRESCRIPTION");
        assertThat(timeline.get(0).subtitle()).contains("v2", "FINALIZED", "Same-day correction", "Follow-up 2026-05-16");
        assertThat(timeline.get(1).itemType()).isEqualTo("CONSULTATION");
        assertThat(timeline.get(1).subtitle()).contains("COMPLETED", "Follow-up 2026-05-15");
        assertThat(timeline.get(2).itemType()).isEqualTo("DOCUMENT");
        assertThat(timeline.get(2).subtitle()).contains("lab.pdf", "AI DONE");
        assertThat(timeline.get(3).itemType()).isEqualTo("DOCUMENT");
        assertThat(timeline.get(3).subtitle()).contains("xray.png", "Dr. Rao", "Metro Imaging");
        assertThat(timeline.get(4).itemType()).isEqualTo("PRESCRIPTION");
        assertThat(timeline.get(4).subtitle()).contains("v1", "SUPERSEDED");
    }

    private PatientRecord patient() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Anita",
                "Patel",
                PatientGender.FEMALE,
                LocalDate.of(1986, 1, 1),
                40,
                "9999999999",
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
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-05-08T00:00:00Z")
        );
    }

    private ClinicalDocumentRecord document(OffsetDateTime createdAt, String filename, ClinicalDocumentType type, String referredDoctor, String referredHospital) {
        return new ClinicalDocumentRecord(
                UUID.randomUUID(),
                TENANT_ID,
                PATIENT_ID,
                CONSULTATION_ID,
                APPOINTMENT_ID,
                ACTOR_ID,
                type,
                filename,
                "application/pdf",
                1024L,
                "checksum",
                "storage-key",
                "notes",
                referredDoctor,
                referredHospital,
                "referral notes",
                "DONE",
                "provider",
                "model",
                BigDecimal.valueOf(0.91),
                "summary",
                "{}",
                "review notes",
                null,
                null,
                ACTOR_ID,
                createdAt,
                "DONE",
                createdAt,
                createdAt
        );
    }

    private ConsultationRecord consultation(OffsetDateTime createdAt, String diagnosis, ConsultationStatus status, LocalDate followUpDate) {
        return new ConsultationRecord(
                CONSULTATION_ID,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Anita Patel",
                DOCTOR_ID,
                "Doctor One",
                APPOINTMENT_ID,
                "Cough",
                "Fever",
                diagnosis,
                "Clinical notes",
                "Advice",
                followUpDate,
                status,
                120,
                80,
                72,
                98.6,
                TemperatureUnit.CELSIUS,
                55.0,
                165.0,
                99,
                18,
                createdAt,
                createdAt,
                createdAt
        );
    }

    private PrescriptionRecord prescription(OffsetDateTime createdAt, Integer version, PrescriptionStatus status, String flowType, String correctionReason, LocalDate followUpDate) {
        return new PrescriptionRecord(
                UUID.randomUUID(),
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Anita Patel",
                DOCTOR_ID,
                "Doctor One",
                CONSULTATION_ID,
                APPOINTMENT_ID,
                "RX-001",
                version,
                version == null ? null : UUID.randomUUID(),
                correctionReason,
                flowType,
                createdAt,
                UUID.randomUUID(),
                createdAt,
                "Diagnosis",
                "Advice",
                followUpDate,
                status,
                createdAt,
                DOCTOR_ID,
                createdAt,
                createdAt,
                createdAt,
                createdAt,
                List.of(new PrescriptionMedicineRecord("Amoxicillin", null, "500 mg", "1 tablet", "Twice daily", "5 days", null, "With food", 1)),
                List.of(new PrescriptionTestRecord("CBC", "Check baseline", 1))
        );
    }
}
