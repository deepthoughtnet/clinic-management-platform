package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.deepthoughtnet.clinic.api.clinicalmemory.service.PatientLongitudinalMemoryService;
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
        patient.update("Asha", "Khan", PatientGender.FEMALE, null, 34, "9999999999", null, null, null, null, null, null, null, null, null, null, "Penicillin", null, null, null, "Uses inhaler", true);

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
        setField(document, "reportDate", java.time.LocalDate.of(2026, 7, 2));
        setField(document, "aiExtractionSummary", "Mild bronchitic changes. No focal consolidation. No pneumonia.");

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
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);

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
        when(longitudinalMemoryService.buildProfile(tenantId, patientId)).thenReturn(new PatientLongitudinalMemoryProfile(
                List.of(new LongitudinalConceptSnapshot("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "HbA1c 8.4")),
                List.of(),
                new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "HbA1c 8.4"),
                new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Blood Sugar", "198", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Random Blood Sugar 198 mg/dL"),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "cholesterol", "Total Cholesterol", "228", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Total Cholesterol 228 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "ldl", "LDL Cholesterol", "152", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "LDL 152 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "triglycerides", "Triglycerides", "238", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Triglycerides 238 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hdl", "HDL Cholesterol", "39", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "HDL 39 mg/dL")
                ),
                null,
                null,
                List.of(
                        new LongitudinalConceptSnapshot("RISK_FLAG", "diabetes_risk", "Diabetes", "Diabetes", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Known diabetic"),
                        new LongitudinalConceptSnapshot("RISK_FLAG", "lipid_risk", "Dyslipidemia", "Dyslipidemia", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 8), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "High cholesterol")
                ),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 1, 15), new java.math.BigDecimal("0.96"), "ACCEPTED", "HbA1c 7.3%"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 7, 10), new java.math.BigDecimal("0.96"), "ACCEPTED", "HbA1c 8.4%"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "creatinine", "Creatinine", "1.08", "mg/dL", "Kidney Function Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 5, 20), new java.math.BigDecimal("0.96"), "ACCEPTED", "Creatinine 1.08 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "Kidney Function Report", "EXTERNAL_LAB_REPORT", document.getId(), java.time.LocalDate.of(2026, 5, 20), new java.math.BigDecimal("0.96"), "ACCEPTED", "eGFR 84 mL/min/1.73m2")
                ),
                "HbA1c 8.4%, Random Blood Sugar 198 mg/dL"
        ));

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
                longitudinalMemoryService,
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
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("HbA1c").contains("8.4"));
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("Blood Sugar").contains("198"));
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("Total Cholesterol").contains("228"));
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("LDL"));
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("HDL"));
        assertThat(context.labIntelligence().abnormalValues())
                .anySatisfy(value -> assertThat(value).contains("Triglycerides"));
        assertThat(context.labIntelligence().latestLabReport()).doesNotContain("null");
        assertThat(context.labIntelligence().lastHbA1c()).contains("8.4");
        assertThat(context.labIntelligence().latestBloodSugar()).contains("198");
        assertThat(context.labIntelligence().latestLipidSummary()).contains("Total Cholesterol", "LDL Cholesterol", "Triglycerides", "HDL Cholesterol");
        assertThat(context.documentIntelligence().radiology()).hasSize(1);
        assertThat(context.timelineSummary().events()).isNotEmpty();
        assertThat(context.longitudinalMemory()).isNotNull();
        assertThat(context.longitudinalMemory().knownConditions()).isNotEmpty();
        assertThat(context.patientSummary().chronicConditions()).contains("Diabetes Mellitus");
        assertThat(context.longitudinalMemory().latestHbA1c()).isNotNull();
        assertThat(context.longitudinalMemory().latestHbA1c().verificationStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(context.longitudinalMemory().riskFlags()).extracting(ClinicalContextResponse.LongitudinalConcept::label).contains("Diabetes", "Dyslipidemia");
        assertThat(context.longitudinalClinicalContext()).isNotNull();
        assertThat(context.longitudinalClinicalContext().labTrends()).hasSize(1);
        assertThat(context.longitudinalClinicalContext().labTrends().get(0).direction()).isEqualTo("WORSENING");
        assertThat(context.longitudinalClinicalContext().imagingHistory()).hasSize(1);
        assertThat(context.longitudinalClinicalContext().imagingHistory().get(0).summary()).contains("bronchitic");
        assertThat(context.longitudinalClinicalContext().renalContext()).isNotNull();
        assertThat(context.longitudinalClinicalContext().renalContext().interpretation()).contains("preserved");
        assertThat(context.aiSummary()).contains("Medication alerts");
        assertThat(context.aiPromptContext()).contains("Patient:").contains("Chronic conditions").contains("INTAKE");
        assertThat(context.clinicalContextJson()).contains("patientSummary");
        assertThat(context.diagnosisHistory().lastVisitDiagnosis()).isNull();
        assertThat(context.previousVisits()).isEmpty();
    }

    @Test
    void buildClinicalContextUsesOnlyCompletedHistoryAndHumanReadableLabTitles() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID currentConsultationId = UUID.randomUUID();
        UUID historicalConsultationId = UUID.randomUUID();

        PatientEntity patient = PatientEntity.create(tenantId, "PAT-2");
        patient.update("Rohan", "Sharma", PatientGender.MALE, null, 42, "9876543210", null, null, null, null, null, null, null, null, null, null, "Penicillin", null, null, null, null, true);

        ConsultationEntity currentConsultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        currentConsultation.update("Fever and cough", "Fever", "Viral URI", "Current draft", "Rest", null, null, null, null, null, null, null, null, null, null);
        setId(currentConsultation, currentConsultationId);
        ConsultationEntity completedConsultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        completedConsultation.update("Older follow-up", "Recovered", "Past viral URI", "Completed", "Follow advice", null, null, null, null, null, null, null, null, null, null);
        completedConsultation.complete();
        setId(completedConsultation, historicalConsultationId);

        ClinicalDocumentEntity document = ClinicalDocumentEntity.create(
                tenantId,
                patientId,
                currentConsultationId,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.EXTERNAL_LAB_REPORT,
                "Diabetes Follow-up Lab Report Retest 3",
                "image/jpeg",
                2048,
                "checksum",
                "patients/1/documents/1/report.jpg",
                "HbA1c 8.4",
                null,
                null,
                null
        );
        setField(document, "uploadSource", "Reception");
        setField(document, "sourceModule", "CLINICAL_INTAKE");
        setField(document, "reportDate", java.time.LocalDate.of(2026, 1, 8));

        LabOrderEntity labOrder = LabOrderEntity.create(
                tenantId,
                "LAB-A1A5C702DE",
                patientId,
                "PAT-2",
                "Rohan Sharma",
                UUID.randomUUID(),
                "Dr. Jain",
                currentConsultationId,
                null,
                null,
                null,
                null,
                null,
                null,
                null
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
        PatientLongitudinalMemoryService longitudinalMemoryService = mock(PatientLongitudinalMemoryService.class);

        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(java.util.Optional.of(patient));
        when(consultationRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(currentConsultation, completedConsultation));
        when(prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of());
        when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, UUID.randomUUID())).thenReturn(List.of());
        when(prescriptionTestRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, UUID.randomUUID())).thenReturn(List.of());
        when(clinicalDocumentRepository.findByTenantIdAndPatientIdAndActiveTrueOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(document));
        when(patientClinicalIntakeRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of());
        when(labOrderRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(labOrder));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, labOrder.getId())).thenReturn(List.of(abnormalResult));
        when(longitudinalMemoryService.buildProfile(tenantId, patientId)).thenReturn(new PatientLongitudinalMemoryProfile(
                List.of(new LongitudinalConceptSnapshot("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Known diabetic")),
                List.of(),
                new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "HbA1c 8.4"),
                new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Blood Sugar", "198", "mg/dL", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Random Blood Sugar 198"),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "cholesterol", "Total Cholesterol", "228", "mg/dL", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Total Cholesterol 228"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "ldl", "LDL Cholesterol", "152", "mg/dL", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "LDL 152"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "triglycerides", "Triglycerides", "238", "mg/dL", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "Triglycerides 238"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hdl", "HDL Cholesterol", "39", "mg/dL", document.getTitle(), document.getDocumentType().name(), document.getId(), document.getReportDate(), new java.math.BigDecimal("0.96"), "PENDING_REVIEW", "HDL 39")
                ),
                null,
                null,
                List.of(),
                List.of(),
                "HbA1c 8.4, Blood Sugar 198"
        ));

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
                longitudinalMemoryService,
                new ObjectMapper()
        );

        ClinicalContextResponse context = service.buildClinicalContext(tenantId, patientId, currentConsultationId);

        assertThat(context.diagnosisHistory().lastVisitDiagnosis()).isEqualTo("Past viral URI");
        assertThat(context.previousVisits()).hasSize(1);
        assertThat(context.documentIntelligence().recentReports()).anySatisfy(report -> assertThat(report).contains("08-Jan-2026", "Diabetes Follow-up Lab Report Retest 3"));
        assertThat(context.labIntelligence().latestLabReport()).doesNotContain("null");
        assertThat(context.labIntelligence().abnormalValues()).anySatisfy(value -> assertThat(value).contains("HbA1c").contains("8.4"));
        assertThat(context.labIntelligence().abnormalValues()).anySatisfy(value -> assertThat(value).contains("Blood Sugar").contains("198"));
        assertThat(context.labIntelligence().abnormalValues()).anySatisfy(value -> assertThat(value).contains("Total Cholesterol").contains("228"));
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
