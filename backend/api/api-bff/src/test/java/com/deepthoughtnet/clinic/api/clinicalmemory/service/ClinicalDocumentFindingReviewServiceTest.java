package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalDocumentFindingReviewServiceTest {
    @Test
    void keepsFindingDecisionsPendingUntilTheDocumentIsCompleted() {
        PatientLongitudinalConceptRepository concepts = mock(PatientLongitudinalConceptRepository.class);
        com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository documents = mock(com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository.class);
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ClinicalDocumentEntity document = mock(ClinicalDocumentEntity.class);
        when(document.getTenantId()).thenReturn(tenantId);
        when(document.getPatientId()).thenReturn(patientId);
        when(document.getId()).thenReturn(documentId);
        when(document.getAiExtractionStructuredJson()).thenReturn("{\"factualFindings\":{\"labResults\":[{\"canonicalKey\":\"hba1c\",\"testName\":\"HbA1c\",\"value\":\"5.9\",\"unit\":\"%\",\"referenceRange\":\"4.0 - 5.6\",\"flag\":\"High\"}]}}");
        when(documents.findByTenantIdAndId(tenantId, documentId)).thenReturn(java.util.Optional.of(document));

        PatientLongitudinalConceptEntity finding = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, documentId, "LAB_REPORT", "Panel", LocalDate.of(2026, 2, 14),
                "LAB_RESULT", "hba1c", "HbA1c", "5.9", "%", "HbA1c 5.9 % 4.0 - 5.6 High",
                "AI extraction", "PENDING_REVIEW", new BigDecimal("0.8"), null);
        PatientLongitudinalConceptEntity second = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, documentId, "LAB_REPORT", "Panel", LocalDate.of(2026, 2, 14),
                "LAB_RESULT", "blood_sugar", "Blood Sugar", "104", "mg/dL", "Blood Sugar 104 mg/dL High",
                "AI extraction", "PENDING_REVIEW", new BigDecimal("0.8"), null);
        when(concepts.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(tenantId, patientId, documentId))
                .thenReturn(List.of(finding, second));
        when(concepts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(concepts.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalDocumentFindingReviewService service = new ClinicalDocumentFindingReviewService(concepts, documents, new ObjectMapper());
        assertThat(service.list(tenantId, documentId).getFirst().originalValue()).isEqualTo("5.9");
        assertThat(service.complete(tenantId, documentId, UUID.randomUUID()).completed()).isFalse();

        ClinicalDocumentFindingReviewService.FindingReviewRecord edited = service.decide(
                tenantId, documentId, finding.getId(), "EDITED", "6.0", "%", "4.0 - 5.6", "HIGH", "Corrected against report", UUID.randomUUID());
        OffsetDateTime reviewedAt = finding.getReviewedAt();

        assertThat(edited.decision()).isEqualTo("EDITED");
        assertThat(edited.originalValue()).isEqualTo("5.9");
        assertThat(edited.value()).isEqualTo("6.0");
        assertThat(finding.getVerificationStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(finding.getReviewDecision()).isEqualTo("EDITED");
        assertThat(service.complete(tenantId, documentId, UUID.randomUUID()).completed()).isFalse();

        service.decide(
                tenantId, documentId, second.getId(), "CONFIRMED", "104", "mg/dL", "70 - 99", "HIGH", "Confirmed against report", UUID.randomUUID());
        ClinicalDocumentFindingReviewService.ReviewCompletion completion = service.complete(tenantId, documentId, UUID.randomUUID());

        assertThat(completion.completed()).isTrue();
        assertThat(finding.getVerificationStatus()).isEqualTo("ACCEPTED");
        assertThat(second.getVerificationStatus()).isEqualTo("ACCEPTED");
        assertThat(finding.getReviewedByAppUserId()).isNotNull();
        assertThat(finding.getReviewedAt()).isEqualTo(reviewedAt);
    }
}
