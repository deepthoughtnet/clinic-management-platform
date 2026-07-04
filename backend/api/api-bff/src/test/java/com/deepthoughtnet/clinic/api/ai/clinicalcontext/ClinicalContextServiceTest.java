package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeEntity;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestRepository;
import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;

class ClinicalContextServiceTest {

    @Test
    void buildClinicalContextAssemblesLongitudinalSummary() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID previousConsultationId = UUID.randomUUID();
        UUID currentPrescriptionId = UUID.randomUUID();
        UUID previousPrescriptionId = UUID.randomUUID();
        UUID labOrderId = UUID.randomUUID();
        UUID intakeId = UUID.randomUUID();

        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("Asha", "Khan", PatientGender.FEMALE, null, 34, "9999999999", null, null, null, null, null, null, null, null, null, null, "Penicillin", "Diabetes", "Metformin", null, "Uses inhaler", true);

        ConsultationEntity currentConsultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        currentConsultation.update("Fever and cough", "Fever", "Viral fever", "Stable", "Rest", null, null, null, null, null, null, null, null, null, null);
        setId(currentConsultation, consultationId);
        ConsultationEntity previousConsultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        previousConsultation.update("Follow-up", "Weakness", "Diabetes follow-up", "Improving", "Continue medicines", null, null, null, null, null, null, null, null, null, null);
        setId(previousConsultation, previousConsultationId);

        PrescriptionEntity currentPrescription = PrescriptionEntity.create(tenantId, patientId, UUID.randomUUID(), consultationId, null, "RX-1");
        currentPrescription.update("Viral fever", "Take rest", null);
        currentPrescription.finalizePrescription(UUID.randomUUID());
        PrescriptionEntity previousPrescription = PrescriptionEntity.create(tenantId, patientId, UUID.randomUUID(), previousConsultationId, null, "RX-2");
        previousPrescription.update("Diabetes follow-up", "Old antibiotic", null);
        previousPrescription.markPrinted();

        PrescriptionMedicineEntity currentMedicine = PrescriptionMedicineEntity.create(tenantId, currentPrescription.getId(), "Amoxicillin", MedicineType.CAPSULE, "500 mg", "1 cap", "1-0-1", "5 days", Timing.AFTER_FOOD, null, 1);
        PrescriptionMedicineEntity previousMedicine = PrescriptionMedicineEntity.create(tenantId, previousPrescription.getId(), "Metformin", MedicineType.TABLET, "500 mg", "1 tab", "1-0-0", "30 days", Timing.AFTER_FOOD, null, 1);
        PrescriptionTestEntity currentTest = PrescriptionTestEntity.create(tenantId, currentPrescription.getId(), "CBC", null, 1);

        ClinicalDocumentEntity document = ClinicalDocumentEntity.create(
                tenantId,
                patientId,
                consultationId,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.RADIOLOGY_REPORT,
                "Chest X-Ray",
                "image/jpeg",
                2048,
                "checksum",
                "patients/1/documents/1/report.jpg",
                "Mild infiltrates",
                null,
                null,
                null
        );
        setField(document, "uploadSource", "RECEPTION");
        setField(document, "sourceModule", "CLINICAL_INTAKE");

        PatientClinicalIntakeEntity intake = PatientClinicalIntakeEntity.create(
                intakeId,
                tenantId,
                patientId,
                consultationId,
                consultationId,
                "Severe headache and fever",
                168.0,
                74.0,
                26.2,
                142,
                92,
                104,
                38.4,
                TemperatureUnit.CELSIUS,
                96,
                22,
                148.0,
                8,
                "Patient reported worsening symptoms in the morning",
                UUID.randomUUID(),
                "Front Desk",
                true,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        LabOrderEntity labOrder = LabOrderEntity.create(
                tenantId,
                "LAB-1",
                patientId,
                "PAT-1",
                "Asha Khan",
                UUID.randomUUID(),
                "Dr. Jain",
                consultationId,
                null,
                null,
                null,
                null,
                null,
                null,
                "CBC"
        );
        labOrder.markReportGenerated(UUID.randomUUID(), "cbc.pdf");
        LabOrderResultEntity abnormalResult = LabOrderResultEntity.create(tenantId, labOrder.getId(), null, "CBC", "CBC", "Hemoglobin", null, "10.2", "g/dL", "12-16", 1, "LOW", true);

        PatientRepository patientRepository = mock(PatientRepository.class);
        ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
        PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
        PrescriptionMedicineRepository prescriptionMedicineRepository = mock(PrescriptionMedicineRepository.class);
        PrescriptionTestRepository prescriptionTestRepository = mock(PrescriptionTestRepository.class);
        ClinicalDocumentRepository clinicalDocumentRepository = mock(ClinicalDocumentRepository.class);
        PatientClinicalIntakeRepository patientClinicalIntakeRepository = mock(PatientClinicalIntakeRepository.class);
        LabOrderRepository labOrderRepository = mock(LabOrderRepository.class);
        LabOrderResultRepository labOrderResultRepository = mock(LabOrderResultRepository.class);

        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(consultationRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(currentConsultation, previousConsultation));
        when(prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(currentPrescription, previousPrescription));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, currentPrescription.getId())).thenReturn(List.of(currentMedicine));
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, previousPrescription.getId())).thenReturn(List.of(previousMedicine));
        when(prescriptionTestRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, currentPrescription.getId())).thenReturn(List.of(currentTest));
        when(prescriptionTestRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, previousPrescription.getId())).thenReturn(List.of());
        when(clinicalDocumentRepository.findByTenantIdAndPatientIdAndActiveTrueOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(document));
        when(patientClinicalIntakeRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(intake));
        when(labOrderRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(labOrder));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, labOrder.getId())).thenReturn(List.of(abnormalResult));

        ClinicalContextService service = new ClinicalContextService(
                patientRepository,
                consultationRepository,
                prescriptionRepository,
                prescriptionMedicineRepository,
                prescriptionTestRepository,
                clinicalDocumentRepository,
                patientClinicalIntakeRepository,
                labOrderRepository,
                labOrderResultRepository,
                new ObjectMapper()
        );

        ClinicalContextResponse context = service.buildClinicalContext(tenantId, patientId, consultationId);

        assertThat(context.patientSummary().patientName()).isEqualTo("Asha | Khan");
        assertThat(context.medicationHistory().alerts()).isNotEmpty();
        assertThat(context.intakeSummary()).isNotNull();
        assertThat(context.intakeSummary().chiefComplaint()).contains("Severe headache");
        assertThat(context.intakeSummary().latestVitals()).isNotNull();
        assertThat(context.intakeSummary().abnormalVitalsAlerts()).isNotEmpty();
        assertThat(context.intakeSummary().uploadedDocumentSummary()).isNotNull();
        assertThat(context.labIntelligence().abnormalValues()).isNotEmpty();
        assertThat(context.documentIntelligence().radiology()).hasSize(1);
        assertThat(context.timelineSummary().events()).isNotEmpty();
        assertThat(context.aiSummary()).contains("Medication alerts");
        assertThat(context.aiPromptContext()).contains("Patient snapshot");
        assertThat(context.clinicalContextJson()).contains("patientSummary");
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to assign entity id for test", ex);
        }
    }

    private static void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to assign entity field for test", ex);
        }
    }
}
