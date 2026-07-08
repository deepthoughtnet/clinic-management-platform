package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptRepository;
import com.deepthoughtnet.clinic.api.clinicalmemory.mapping.ClinicalConceptMapper;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PatientLongitudinalMemoryServiceTest {

    @Test
    void ingestPendingConceptsCreatesPendingLongitudinalMemory() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        ClinicalDocumentEntity document = document("Diabetes Follow-up Laboratory Report", LocalDate.of(2026, 1, 8));

        service.ingestPendingConcepts(
                document,
                "{\"conditions\":[\"Diabetes Mellitus\",\"Diabetes Mellitus\"],\"labs\":{\"hba1c\":\"8.4\",\"bloodSugar\":\"198\",\"lipid\":[\"High Cholesterol\",\"High LDL\",\"High Triglycerides\",\"Low HDL\"]}}",
                "Known diabetic\nHbA1c 8.4\nRandom Blood Sugar 198\nLipid profile high cholesterol high LDL high triglycerides low HDL",
                new BigDecimal("0.96"),
                "Clinical extraction"
        );

        assertThat(saved.get()).isNotEmpty();
        assertThat(saved.get()).filteredOn(concept -> "PENDING_REVIEW".equals(concept.getVerificationStatus())).isNotEmpty();
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .contains("diabetes_mellitus", "hba1c", "blood_sugar", "cholesterol", "ldl", "triglycerides", "hdl", "diabetes_risk", "lipid_risk");
        assertThat(saved.get().stream().filter(concept -> "diabetes_mellitus".equals(concept.getConceptKey())).count()).isEqualTo(1);
        assertThat(saved.get().stream()
                .filter(concept -> "ldl".equals(concept.getConceptKey()))
                .map(PatientLongitudinalConceptEntity::getValueText))
                .contains("High LDL");
        verify(repository).deleteByDocumentAndStatus(eq(document.getTenantId()), eq(document.getPatientId()), eq(document.getId()), eq("PENDING_REVIEW"));
    }

    @Test
    void ingestPendingConceptsReplacesExistingPendingConceptsForSameDocument() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        List<PatientLongitudinalConceptEntity> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            persisted.removeIf(concept -> concept.getSourceDocumentId() != null && concept.getSourceDocumentId().equals(value.getFirst().getSourceDocumentId()));
            persisted.addAll(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        doAnswer(invocation -> {
            UUID sourceDocumentId = invocation.getArgument(2);
            persisted.removeIf(concept -> sourceDocumentId.equals(concept.getSourceDocumentId())
                    && "PENDING_REVIEW".equals(concept.getVerificationStatus()));
            return 1;
        }).when(repository).deleteByDocumentAndStatus(any(), any(), any(), any());

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        ClinicalDocumentEntity document = document("Diabetes Follow-up Laboratory Report", LocalDate.of(2026, 1, 8));
        String extractedJson = "{\"conditions\":[\"Diabetes Mellitus\"],\"labs\":{\"hba1c\":\"8.4\",\"bloodSugar\":\"198\"}}";
        String ocrText = "Known diabetic\nHbA1c 8.4\nRandom Blood Sugar 198";

        service.ingestPendingConcepts(document, extractedJson, ocrText, new BigDecimal("0.96"), "Clinical extraction");
        service.ingestPendingConcepts(document, extractedJson, ocrText, new BigDecimal("0.96"), "Clinical extraction");

        assertThat(persisted).extracting(PatientLongitudinalConceptEntity::getVerificationStatus).containsOnly("PENDING_REVIEW");
        assertThat(persisted.stream().map(PatientLongitudinalConceptEntity::getConceptKey).toList())
                .contains("diabetes_mellitus", "hba1c", "blood_sugar", "diabetes_risk");
        assertThat(persisted).hasSize(5);
    }

    @Test
    void ingestPendingConceptsPreservesLongTextFields() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        String longTitle = "Diabetes Follow-up Laboratory Report " + "A".repeat(280);
        ClinicalDocumentEntity document = document(longTitle, LocalDate.of(2026, 1, 8));
        String longCondition = "diabetes " + "B".repeat(300);
        String longSourceSummary = "Clinical extraction " + "C".repeat(300);

        service.ingestPendingConcepts(
                document,
                "{\"conditions\":[\"" + longCondition + "\"],\"labs\":{\"hba1c\":\"8.4\"}}",
                "Known diabetic\nHbA1c 8.4",
                new BigDecimal("0.96"),
                longSourceSummary
        );

        assertThat(saved.get()).isNotEmpty();
        assertThat(saved.get().getFirst().getSourceDocumentTitle()).hasSizeGreaterThan(256);
        assertThat(saved.get().stream().map(PatientLongitudinalConceptEntity::getConceptLabel).anyMatch(label -> label != null && label.length() > 256)).isTrue();
        assertThat(saved.get().stream().map(PatientLongitudinalConceptEntity::getEvidenceText).anyMatch(text -> text != null && text.length() > 256)).isTrue();
        assertThat(saved.get().stream().map(PatientLongitudinalConceptEntity::getSourceSummary).anyMatch(text -> text != null && text.length() > 256)).isTrue();
    }

    @Test
    void verifyAndProfileKeepsHistoryAndUsesLatestAcceptedValues() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity olderDocument = document(tenantId, patientId, "Older report", LocalDate.of(2025, 11, 1));
        ClinicalDocumentEntity newerDocument = document(tenantId, patientId, "Newer report", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity acceptedOlderHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                olderDocument.getId(),
                olderDocument.getDocumentType().name(),
                olderDocument.getTitle(),
                olderDocument.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "7.6",
                "%",
                "HbA1c 7.6",
                "Older report",
                "ACCEPTED",
                new BigDecimal("0.91"),
                olderDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedNewerHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedCondition = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "CONDITION",
                "diabetes_mellitus",
                "Diabetes Mellitus",
                "Diabetes Mellitus",
                null,
                "Known diabetic",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedTotalCholesterol = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "cholesterol",
                "Total Cholesterol",
                "228",
                "mg/dL",
                "Total Cholesterol 228",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedLdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "ldl",
                "LDL Cholesterol",
                "152",
                "mg/dL",
                "LDL 152",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedHdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "hdl",
                "HDL Cholesterol",
                "39",
                "mg/dL",
                "HDL 39",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedTriglycerides = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "triglycerides",
                "Triglycerides",
                "238",
                "mg/dL",
                "Triglycerides 238",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedDiabetesRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "RISK_FLAG",
                "diabetes_risk",
                "Diabetes",
                "Diabetes",
                null,
                "Known diabetic",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedLipidRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "RISK_FLAG",
                "lipid_risk",
                "Dyslipidemia",
                "Dyslipidemia",
                null,
                "High LDL",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity rejectedConcept = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Newer report",
                "REJECTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        rejectedConcept.markVerified("REJECTED", UUID.randomUUID(), "Rejected", null);

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(
                acceptedNewerHba1c,
                acceptedBloodSugar,
                acceptedCondition,
                acceptedTotalCholesterol,
                acceptedLdl,
                acceptedHdl,
                acceptedTriglycerides,
                acceptedDiabetesRisk,
                acceptedLipidRisk,
                rejectedConcept,
                acceptedOlderHba1c
        ));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.knownConditions()).hasSize(1);
        assertThat(profile.longTermMedications()).isEmpty();
        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().valueText()).isEqualTo("8.4");
        assertThat(profile.latestBloodSugar()).isNotNull();
        assertThat(profile.latestBloodSugar().valueText()).isEqualTo("198");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::label)
                .contains("Total Cholesterol", "LDL Cholesterol", "HDL Cholesterol", "Triglycerides");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::valueText)
                .contains("228", "152", "39", "238");
        assertThat(profile.riskFlags()).extracting(LongitudinalConceptSnapshot::label).contains("Diabetes", "Dyslipidemia");
        assertThat(profile.history()).hasSize(10);
        assertThat(profile.mostRecentLaboratorySummary()).contains("HbA1c 8.4");

        when(repository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(tenantId, patientId, newerDocument.getId()))
                .thenReturn(List.of(
                        PatientLongitudinalConceptEntity.create(
                                tenantId,
                                patientId,
                                newerDocument.getId(),
                                newerDocument.getDocumentType().name(),
                                newerDocument.getTitle(),
                                newerDocument.getReportDate(),
                                "LAB_RESULT",
                                "hba1c",
                                "HbA1c",
                                "8.4",
                                "%",
                                "HbA1c 8.4",
                                "Newer report",
                                "PENDING_REVIEW",
                                new BigDecimal("0.96"),
                                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                        )
                ));

        service.verifyDocumentConcepts(newerDocument, true, UUID.randomUUID(), "{\"hba1c\":\"8.4\"}", "Accepted", null);
        verify(repository).saveAllAndFlush(anyList());
    }

    @Test
    void buildProfileSurfacesPendingConceptsWithoutDiscardingLabs() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Diabetes Follow-up Lab Report", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity pendingCondition = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "CONDITION",
                "diabetes_mellitus",
                "Diabetes Mellitus",
                "Diabetes Mellitus",
                null,
                "Known diabetic",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingHbA1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingLdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "ldl",
                "LDL",
                "152",
                "mg/dL",
                "LDL 152",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "RISK_FLAG",
                "diabetes_risk",
                "Diabetes",
                "Diabetes",
                null,
                "Known diabetic",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(
                pendingRisk,
                pendingLdl,
                pendingBloodSugar,
                pendingHbA1c,
                pendingCondition
        ));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.knownConditions()).extracting(concept -> concept.verificationStatus()).containsExactly("PENDING_REVIEW");
        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().valueText()).isEqualTo("8.4");
        assertThat(profile.latestHbA1c().verificationStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(profile.latestBloodSugar()).isNotNull();
        assertThat(profile.latestBloodSugar().valueText()).isEqualTo("198");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::conceptKey).contains("ldl");
        assertThat(profile.riskFlags()).extracting(LongitudinalConceptSnapshot::label).contains("Diabetes");
        assertThat(profile.history()).hasSize(5);
    }

    private ClinicalDocumentEntity document(String title, LocalDate reportDate) {
        return document(UUID.randomUUID(), UUID.randomUUID(), title, reportDate);
    }

    private ClinicalDocumentEntity document(UUID tenantId, UUID patientId, String title, LocalDate reportDate) {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                UUID.randomUUID(),
                tenantId,
                patientId,
                null,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.LAB_REPORT,
                title,
                "notes",
                reportDate,
                "Uploader",
                "RECEPTION",
                "report.pdf",
                "application/pdf",
                100,
                "bucket",
                "storage-key-" + UUID.randomUUID(),
                "checksum",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        return entity;
    }
}
