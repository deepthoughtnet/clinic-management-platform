package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationSafetyServiceTest {
    @Mock ConsultationRepository consultationRepository;
    @Mock PrescriptionRepository prescriptionRepository;
    @Mock PrescriptionMedicineRepository prescriptionMedicineRepository;
    @Mock PatientRepository patientRepository;
    @Mock MedicineRepository medicineRepository;
    @Mock ClinicalContextService clinicalContextService;
    @Mock MedicationSafetyEngine medicationSafetyEngine;
    MedicationSafetySnapshotHasher medicationSafetySnapshotHasher = new MedicationSafetySnapshotHasher();
    @Mock AuditEventPublisher auditEventPublisher;

    MedicationSafetyService medicationSafetyService;

    @BeforeEach
    void setUp() {
        medicationSafetyService = new MedicationSafetyService(
                consultationRepository,
                prescriptionRepository,
                prescriptionMedicineRepository,
                patientRepository,
                medicineRepository,
                clinicalContextService,
                medicationSafetyEngine,
                medicationSafetySnapshotHasher,
                auditEventPublisher
        );
    }

    @Test
    void assemblesRequestFromPersistedPrescriptionRows() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity medicine = MedicineEntity.create(tenantId, "Paracetamol", "TABLET");
        medicine.update("Paracetamol", "TABLET", null, null, null, "Paracetamol", null, "ANALGESIC", "Tablet", "500 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol", MedicineType.TABLET, "500 mg", "1 tab", "1-0-1", "5 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        UUID renalDocumentId = UUID.randomUUID();
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
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
                OffsetDateTime.now()
        ));

        MedicationSafetyEvaluationResult expected = new MedicationSafetyEvaluationResult(
                "eval-1",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "UNAVAILABLE"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        );
        when(medicationSafetyEngine.evaluate(any())).thenReturn(expected);

        MedicationSafetyEvaluationResult result = medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.proposedMedications()).hasSize(1);
        assertThat(request.proposedMedications().get(0).medicineName()).isEqualTo("Paracetamol");
        assertThat(request.proposedMedications().get(0).medicineId()).isEqualTo(medicine.getId().toString());
    }

    @Test
    void derivesConservativeIngredientFallbackFromStructuredMedicineName() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity medicine = MedicineEntity.create(tenantId, "Paracetamol 500 mg", "TABLET");
        medicine.update("Paracetamol 500 mg", "TABLET", null, null, null, null, null, "ANALGESIC", "Tablet", "500 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol 500 mg", MedicineType.TABLET, "500 mg", "1 tab", "1-0-1", "5 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
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
                OffsetDateTime.now()
        ));
        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-ingredient-fallback",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "UNAVAILABLE"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.proposedMedications()).hasSize(1);
        assertThat(request.proposedMedications().get(0).activeIngredients()).containsExactly("paracetamol");
        assertThat(request.proposedMedications().get(0).normalizedMedicineName()).isEqualTo("paracetamol");
    }

    @Test
    void passesLongitudinalRenalContextIntoMedicationSafetyRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity medicine = MedicineEntity.create(tenantId, "Amlodipine", "TABLET");
        medicine.update("Amlodipine", "TABLET", null, null, null, "Amlodipine", null, "ANTIHYPERTENSIVE", "Tablet", "5 mg", "mg", null, "1 tab", "1-0-0", 30, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Amlodipine", MedicineType.TABLET, "5 mg", "1 tab", "1-0-0", "30 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        UUID renalDocumentId = UUID.randomUUID();
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                new ClinicalContextResponse.LongitudinalMemory(
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(
                                new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "creatinine", "Creatinine", "1.08", "mg/dL", "03_Kidney_Function_Report", "EXTERNAL_LAB_REPORT", renalDocumentId.toString(), "2026-05-20", new BigDecimal("0.18"), "PENDING_REVIEW", "Creatinine 1.08 mg/dL"),
                                new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "03_Kidney_Function_Report", "EXTERNAL_LAB_REPORT", renalDocumentId.toString(), "2026-05-20", new BigDecimal("0.18"), "PENDING_REVIEW", "eGFR 84 mL/min/1.73m2")
                        ),
                        "Kidney Function Report"
                ),
                new ClinicalContextResponse.LongitudinalClinicalContext(
                        List.of(),
                        List.of(),
                        new ClinicalContextResponse.RenalContext("1.08 mg/dL", "2026-05-20", "84 mL/min/1.73m2", "2026-05-20", "Historical renal function preserved.", 52, "PENDING_VERIFICATION", List.of(renalDocumentId.toString())),
                        List.of(),
                        List.of()
                ),
                null,
                null,
                null,
                OffsetDateTime.now()
        ));
        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-renal",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "PARTIAL"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.renalContext()).isNotNull();
        assertThat(request.renalContext().creatinine()).isEqualTo("1.08 mg/dL");
        assertThat(request.renalContext().egfr()).isEqualTo("84 mL/min/1.73m2");
        assertThat(request.renalContext().verificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(request.renalContext().sourceReferences()).containsExactly("03_Kidney_Function_Report");
        assertThat(request.renalContext().sourceDocumentIds()).containsExactly(renalDocumentId.toString());
    }

    @Test
    void fallsBackToHumanizedSourceTypeWhenRenalTitleIsMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID renalDocumentId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity medicine = MedicineEntity.create(tenantId, "Amlodipine", "TABLET");
        medicine.update("Amlodipine", "TABLET", null, null, null, "Amlodipine", null, "ANTIHYPERTENSIVE", "Tablet", "5 mg", "mg", null, "1 tab", "1-0-0", 30, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Amlodipine", MedicineType.TABLET, "5 mg", "1 tab", "1-0-0", "30 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                new ClinicalContextResponse.LongitudinalMemory(
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(
                                new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "creatinine", "Creatinine", "1.08", "mg/dL", "", "LONGITUDINAL_MEMORY", renalDocumentId.toString(), "2026-05-20", new BigDecimal("0.18"), "PENDING_REVIEW", "Creatinine 1.08 mg/dL")
                        ),
                        "Kidney Function Report"
                ),
                new ClinicalContextResponse.LongitudinalClinicalContext(
                        List.of(),
                        List.of(),
                        new ClinicalContextResponse.RenalContext("1.08 mg/dL", "2026-05-20", null, null, "Historical renal function preserved.", 52, "PENDING_VERIFICATION", List.of(renalDocumentId.toString())),
                        List.of(),
                        List.of()
                ),
                null,
                null,
                null,
                OffsetDateTime.now()
        ));
        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-renal-2",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "PARTIAL"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.renalContext().sourceReferences()).containsExactly("Longitudinal Memory");
    }

    @Test
    void enrichesDistinctCatalogueProductsWithoutCollapsingStrengthVariants() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);

        MedicineEntity paracetamol650 = MedicineEntity.create(tenantId, "Paracetamol 650mg Tablet", "TABLET");
        paracetamol650.update("Paracetamol 650mg Tablet", "TABLET", null, null, null, "Paracetamol", null, "ANALGESIC", "Tablet", "650 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        MedicineEntity paracetamol500 = MedicineEntity.create(tenantId, "Paracetamol 500 mg Tablet", "TABLET");
        paracetamol500.update("Paracetamol 500 mg Tablet", "TABLET", null, null, null, "Paracetamol", null, "ANALGESIC", "Tablet", "500 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line1 = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol 650mg Tablet", MedicineType.TABLET, "650 mg", "1 tab", "1-0-1", "3 days", Timing.AFTER_FOOD, null, 1);
        PrescriptionMedicineEntity line2 = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol 500 mg Tablet", MedicineType.TABLET, "500 mg", "1 tab", "1-0-1", "3 days", Timing.AFTER_FOOD, null, 2);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line1, line2));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(paracetamol500, paracetamol650));
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
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
                OffsetDateTime.now()
        ));
        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-2b",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, false, true, true, false, false, true, false, false, "UNAVAILABLE"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.proposedMedications()).hasSize(2);
        assertThat(request.proposedMedications())
                .extracting(MedicationSafetyMedicationItem::medicineId)
                .containsExactly(paracetamol650.getId().toString(), paracetamol500.getId().toString());
        assertThat(request.proposedMedications())
                .extracting(MedicationSafetyMedicationItem::strength)
                .containsExactly("650 mg", "500 mg");
    }

    @Test
    void filtersProposedMedicineFromCurrentMedicationOverlapSnapshot() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity paracetamol = MedicineEntity.create(tenantId, "Paracetamol", "TABLET");
        paracetamol.update("Paracetamol", "TABLET", null, null, null, "Paracetamol", null, "ANALGESIC", "Tablet", "500 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        MedicineEntity amlodipine = MedicineEntity.create(tenantId, "Amlodipine", "TABLET");
        amlodipine.update("Amlodipine", "TABLET", null, null, null, "Amlodipine", null, "ANTIHYPERTENSIVE", "Tablet", "5 mg", "mg", null, "1 tab", "1-0-0", 30, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol", MedicineType.TABLET, "500 mg", "1 tab", "1-0-1", "5 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(paracetamol, amlodipine));
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                new ClinicalContextResponse.PatientSnapshot(null, null, null, null, null, List.of("Paracetamol", "Amlodipine"), null),
                List.of(),
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
                OffsetDateTime.now()
        ));

        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-2",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "UNAVAILABLE"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.currentMedications()).extracting(MedicationSafetyMedicationItem::medicineName).containsExactly("Amlodipine");
    }

    @Test
    void parsesPersistedPatientAllergyTextIntoSafetySnapshot() {
        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();

        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update(
                "Rohan",
                "Sharma",
                null,
                null,
                null,
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
                "Paracetamol",
                null,
                null,
                null,
                null,
                true
        );
        PrescriptionEntity prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
        prescription.update("Diagnosis", "Advice", null);
        MedicineEntity paracetamol = MedicineEntity.create(tenantId, "Paracetamol", "TABLET");
        paracetamol.update("Paracetamol", "TABLET", null, null, null, "Paracetamol", null, "ANALGESIC", "Tablet", "500 mg", "mg", null, "1 tab", "1-0-1", 5, "AFTER_FOOD", null, BigDecimal.ONE, BigDecimal.ZERO, true);
        PrescriptionMedicineEntity line = PrescriptionMedicineEntity.create(tenantId, prescription.getId(), "Paracetamol", MedicineType.TABLET, "500 mg", "1 tab", "1-0-1", "5 days", Timing.AFTER_FOOD, null, 1);

        when(consultationRepository.findByTenantIdAndId(tenantId, consultationId)).thenReturn(java.util.Optional.of(consultation));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())).thenReturn(List.of(line));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(paracetamol));
        when(clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId)).thenReturn(new ClinicalContextResponse(
                tenantId,
                patient.getId(),
                consultationId,
                null,
                List.of(),
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
                OffsetDateTime.now()
        ));

        when(medicationSafetyEngine.evaluate(any())).thenAnswer(invocation -> new MedicationSafetyEvaluationResult(
                "eval-allergy",
                OffsetDateTime.now(),
                prescription.getId(),
                MedicationSafetySeverity.NONE,
                List.of(),
                List.of(),
                new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, false, true, "UNAVAILABLE"),
                "med-safety-v1",
                new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
        ));

        medicationSafetyService.evaluateForConsultation(tenantId, consultationId, UUID.randomUUID());

        ArgumentCaptor<MedicationSafetyEvaluationRequest> captor = ArgumentCaptor.forClass(MedicationSafetyEvaluationRequest.class);
        verify(medicationSafetyEngine).evaluate(captor.capture());
        MedicationSafetyEvaluationRequest request = captor.getValue();
        assertThat(request.allergies()).isNotNull();
        assertThat(request.allergies().unknown()).isFalse();
        assertThat(request.allergies().noKnownAllergy()).isFalse();
        assertThat(request.allergies().terms()).containsExactly("Paracetamol");
    }
}
