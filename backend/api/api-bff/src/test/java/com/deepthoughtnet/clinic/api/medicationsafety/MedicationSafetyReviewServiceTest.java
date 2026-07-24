package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewEntity;
import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewRepository;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MedicationSafetyReviewServiceTest {
    @Mock MedicationSafetyService medicationSafetyService;
    @Mock MedicationSafetyEngine medicationSafetyEngine;
    @Mock PrescriptionSafetyReviewRepository reviewRepository;
    @Mock AuditEventPublisher auditEventPublisher;
    @Mock PermissionChecker permissionChecker;
    @Mock TenantUserManagementService tenantUserManagementService;

    MedicationSafetyReviewService medicationSafetyReviewService;
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        lenient().when(medicationSafetyEngine.rulesVersion()).thenReturn("med-safety-v1");
        medicationSafetyReviewService = new MedicationSafetyReviewService(
                medicationSafetyService,
                medicationSafetyEngine,
                reviewRepository,
                auditEventPublisher,
                permissionChecker,
                tenantUserManagementService,
                objectMapper
        );
    }

    @Test
    void blocksFinalizationWithoutAnyReview() {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> medicationSafetyReviewService.assertFinalizationReady(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId))
                .isInstanceOf(MedicationSafetyGuardException.class)
                .extracting(ex -> ((MedicationSafetyGuardException) ex).getCode())
                .isEqualTo("SAFETY_REVIEW_REQUIRED");
    }

    @Test
    void requiresAcknowledgementForWarnings() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, null, null
                ))),
                null,
                null,
                null
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        medicationSafetyReviewService.assertFinalizationReady(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void blocksWarningFinalizationWhenNotAcknowledged() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), false, false, null, null
                ))),
                null,
                null,
                null
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        assertThatThrownBy(() -> medicationSafetyReviewService.assertFinalizationReady(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId))
                .isInstanceOf(MedicationSafetyGuardException.class)
                .extracting(ex -> ((MedicationSafetyGuardException) ex).getCode())
                .isEqualTo("SAFETY_WARNING_ACKNOWLEDGEMENT_REQUIRED");
    }

    @Test
    void blocksCriticalOverrideForUnauthorizedRole() throws Exception {
        SafetyFixture fixture = fixtureWithCriticalFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(permissionChecker.hasAnyRole(any(), any(), any(), any())).thenReturn(false);

        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.CRITICAL_OVERRIDE_APPROVED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), false, true, "BENEFIT_OUTWEIGHS_RISK", "Clinical reason provided"
                ))),
                "BENEFIT_OUTWEIGHS_RISK",
                "Clinical reason provided",
                fixture.finding.category().name()
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        assertThatThrownBy(() -> medicationSafetyReviewService.assertFinalizationReady(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId))
                .isInstanceOf(MedicationSafetyGuardException.class)
                .extracting(ex -> ((MedicationSafetyGuardException) ex).getCode())
                .isEqualTo("SAFETY_OVERRIDE_NOT_AUTHORIZED");
    }

    @Test
    void allowsCriticalOverrideForAuthorizedDoctor() throws Exception {
        SafetyFixture fixture = fixtureWithCriticalFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(permissionChecker.hasAnyRole(any(), any(), any(), any())).thenReturn(true);

        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.CRITICAL_OVERRIDE_APPROVED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), false, true, "BENEFIT_OUTWEIGHS_RISK", "Clinical reason provided"
                ))),
                "BENEFIT_OUTWEIGHS_RISK",
                "Clinical reason provided",
                fixture.finding.category().name()
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        medicationSafetyReviewService.assertFinalizationReady(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void flagsStaleReviewWhenPrescriptionChanged() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        PrescriptionSafetyReviewEntity staleReview = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash() + "-stale",
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, null, null
                ))),
                null,
                null,
                null
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(staleReview));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId);

        assertThat(response.stale()).isTrue();
        assertThat(response.readyForFinalization()).isFalse();
        assertThat(response.requiredAction()).isEqualTo("RERUN_SAFETY_CHECK");
    }

    @Test
    void doesNotMarkMissingReviewAsStale() {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.empty());

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId);

        assertThat(response.stale()).isFalse();
        assertThat(response.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name());
        assertThat(response.readyForFinalization()).isFalse();
    }

    @Test
    void activeDraftReviewQueryUsesLatestReviewAndDoesNotRewindToOlderExactMatch() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        MedicationSafetyEvaluationContext changedContext = new MedicationSafetyEvaluationContext(
                fixture.context.consultation(),
                fixture.context.patient(),
                fixture.context.prescription(),
                fixture.context.clinicalContext(),
                fixture.context.request(),
                fixture.context.prescriptionHash() + "-changed",
                fixture.context.patientContextHash(),
                fixture.context.snapshotHash()
        );
        PrescriptionSafetyReviewEntity persisted = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name(),
                fixture.actorAppUserId,
                null,
                null,
                null,
                null
        );
        PrescriptionSafetyReviewEntity olderExactMatch = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                UUID.randomUUID().toString(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name(),
                fixture.actorAppUserId,
                null,
                null,
                null,
                null
        );

        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(changedContext);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(persisted));
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId, fixture.prescription.getId());

        assertThat(response.reviewId()).isEqualTo(persisted.getId());
        assertThat(response.stale()).isTrue();
        assertThat(response.readyForFinalization()).isFalse();
        assertThat(response.requiredAction()).isEqualTo("RERUN_SAFETY_CHECK");
        assertThat(olderExactMatch.getPrescriptionHash()).isEqualTo(fixture.context.prescriptionHash());
    }

    @Test
    void evaluateAndPersistCreatesSnapshotForActiveDraft() throws Exception {
        SafetyFixture fixture = new SafetyFixture("f-info", MedicationSafetySeverity.INFO, MedicationSafetyFindingCategory.DUPLICATE_MEDICATION);
        when(medicationSafetyService.evaluateForConsultation(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId)).thenReturn(fixture.current);
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationSafetyEvaluationResult result = medicationSafetyReviewService.evaluateAndPersist(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        ArgumentCaptor<PrescriptionSafetyReviewEntity> captor = ArgumentCaptor.forClass(PrescriptionSafetyReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        PrescriptionSafetyReviewEntity saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(fixture.tenantId);
        assertThat(saved.getConsultationId()).isEqualTo(fixture.context.consultation().getId());
        assertThat(saved.getPatientId()).isEqualTo(fixture.context.patient().getId());
        assertThat(saved.getPrescriptionId()).isEqualTo(fixture.prescription.getId());
        assertThat(saved.getPrescriptionVersion()).isEqualTo(fixture.prescription.getVersionNumber());
        assertThat(saved.getPrescriptionHash()).isEqualTo(fixture.context.prescriptionHash());
        assertThat(saved.getPatientContextHash()).isEqualTo(fixture.context.patientContextHash());
        assertThat(saved.getRulesVersion()).isEqualTo(fixture.current.rulesVersion());
        assertThat(saved.getEvaluationId()).isEqualTo(fixture.current.evaluationId());
        assertThat(saved.getEvaluationOverallSeverity()).isEqualTo(MedicationSafetySeverity.INFO.name());
        assertThat(saved.getDecisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name());
        assertThat(saved.getReviewedByAppUserId()).isNull();
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getSnapshotGeneration()).isEqualTo(1);
        assertThat(saved.getEvaluationSnapshotJson()).isNotBlank();
        assertThat(result.evaluationId()).isEqualTo(fixture.current.evaluationId());

        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(saved));
        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId);
        assertThat(response.decisionStatus()).isEqualTo(saved.getDecisionStatus());
        assertThat(response.stale()).isFalse();
        assertThat(response.readyForFinalization()).isTrue();
    }

    @Test
    void runSafetyCheckRejectsUnsavedPrescriptionBeforeInvokingEngine() {
        SafetyFixture fixture = fixtureWithWarningFinding();
        MedicationSafetyEvaluationContext noPrescriptionContext = new MedicationSafetyEvaluationContext(
                fixture.context.consultation(),
                fixture.context.patient(),
                null,
                fixture.context.clinicalContext(),
                fixture.context.request(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.context.snapshotHash()
        );
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(noPrescriptionContext);

        assertThatThrownBy(() -> medicationSafetyReviewService.runSafetyCheck(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId))
                .isInstanceOf(MedicationSafetyGuardException.class)
                .extracting(ex -> ((MedicationSafetyGuardException) ex).getCode())
                .isEqualTo("PRESCRIPTION_NOT_SAVED");

        verify(medicationSafetyService, never()).evaluateForConsultation(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void runSafetyCheckPersistsSnapshotForCurrentDraftAndReadPathMarksItStaleAfterChange() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        MedicationSafetyEvaluationContext changedContext = new MedicationSafetyEvaluationContext(
                fixture.context.consultation(),
                fixture.context.patient(),
                fixture.context.prescription(),
                fixture.context.clinicalContext(),
                fixture.context.request(),
                fixture.context.prescriptionHash() + "-changed",
                fixture.context.patientContextHash(),
                fixture.context.snapshotHash()
        );

        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context, changedContext);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationSafetyReviewResponse captured = medicationSafetyReviewService.runSafetyCheck(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        ArgumentCaptor<PrescriptionSafetyReviewEntity> savedCaptor = ArgumentCaptor.forClass(PrescriptionSafetyReviewEntity.class);
        verify(reviewRepository).save(savedCaptor.capture());
        PrescriptionSafetyReviewEntity saved = savedCaptor.getValue();
        assertThat(saved.getPrescriptionHash()).isEqualTo(fixture.context.prescriptionHash());
        assertThat(saved.getDecisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name());
        assertThat(captured.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name());

        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(saved));

        MedicationSafetyReviewResponse stale = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId);

        assertThat(stale.stale()).isTrue();
        assertThat(stale.readyForFinalization()).isFalse();
        assertThat(stale.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name());
    }

    @Test
    void runSafetyCheckPersistsInfoOnlySnapshotForActiveDraft() throws Exception {
        SafetyFixture fixture = new SafetyFixture("f-info", MedicationSafetySeverity.INFO, MedicationSafetyFindingCategory.DUPLICATE_MEDICATION);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.runSafetyCheck(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        ArgumentCaptor<PrescriptionSafetyReviewEntity> captor = ArgumentCaptor.forClass(PrescriptionSafetyReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        PrescriptionSafetyReviewEntity saved = captor.getValue();
        assertThat(saved.getPrescriptionId()).isEqualTo(fixture.prescription.getId());
        assertThat(saved.getDecisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name());
        assertThat(response.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name());
        assertThat(response.readyForFinalization()).isTrue();
        assertThat(response.stale()).isFalse();
    }

    @Test
    void runSafetyCheckReusesLatestCurrentSnapshotWithoutCreatingDuplicate() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        PrescriptionSafetyReviewEntity currentReview = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name(),
                fixture.actorAppUserId,
                null,
                null,
                null,
                null
        );
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(currentReview));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.runSafetyCheck(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        verify(reviewRepository, never()).save(any());
        assertThat(response.reviewId()).isEqualTo(currentReview.getId());
        assertThat(response.stale()).isFalse();
        assertThat(response.readyForFinalization()).isTrue();
    }

    @Test
    void runSafetyCheckCreatesNewSnapshotWhenLatestReviewIsStaleEvenIfEarlierMatchingReviewExists() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        PrescriptionSafetyReviewEntity staleLatest = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash() + "-previous",
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name(),
                fixture.actorAppUserId,
                null,
                null,
                null,
                null
        );
        staleLatest.markStale();

        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(staleLatest));
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.runSafetyCheck(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        ArgumentCaptor<PrescriptionSafetyReviewEntity> captor = ArgumentCaptor.forClass(PrescriptionSafetyReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        PrescriptionSafetyReviewEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotEqualTo(staleLatest.getId());
        assertThat(saved.getSnapshotGeneration()).isEqualTo(staleLatest.getSnapshotGeneration() + 1);
        assertThat(response.reviewId()).isEqualTo(saved.getId());
        assertThat(response.stale()).isFalse();
    }

    @Test
    void finalizedReviewRetainsAcknowledgementsAndIsNotMarkedReadyForFinalization() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, "DUPLICATE_INTENTIONAL", "Duplicate intentional"
                ))),
                null,
                null,
                null
        );
        review.markFinalized();
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId);

        assertThat(response.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.FINALIZED.name());
        assertThat(response.readyForFinalization()).isFalse();
        assertThat(response.stale()).isFalse();
        assertThat(response.findingReviews()).hasSize(1);
        assertThat(response.findingReviews().get(0).acknowledged()).isTrue();
        assertThat(response.findingReviews().get(0).reasonCode()).isEqualTo("DUPLICATE_INTENTIONAL");
        assertThat(response.findingReviews().get(0).reasonText()).isEqualTo("Duplicate intentional");
        assertThat(response.reviewedByDisplayName()).isEqualTo("User unavailable");
    }

    @Test
    void markFinalizedPreservesAcknowledgementSnapshot() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(medicationSafetyEngine.evaluate(any())).thenReturn(fixture.current);
        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, "DUPLICATE_INTENTIONAL", "Duplicate intentional"
                ))),
                null,
                null,
                null
        );
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        medicationSafetyReviewService.markFinalized(fixture.tenantId, fixture.consultationId, fixture.actorAppUserId);

        ArgumentCaptor<PrescriptionSafetyReviewEntity> captor = ArgumentCaptor.forClass(PrescriptionSafetyReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        PrescriptionSafetyReviewEntity saved = captor.getValue();
        assertThat(saved.getDecisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.FINALIZED.name());
        assertThat(saved.getAcknowledgementJson()).isNotBlank();
        assertThat(saved.getAcknowledgementJson()).contains("DUPLICATE_INTENTIONAL");
        assertThat(saved.getFinalizedAt()).isNotNull();
    }

    @Test
    void finalizedReviewUsesPersistedSnapshotForVersionSpecificLookup() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        when(tenantUserManagementService.list(fixture.tenantId)).thenReturn(List.of(
                new TenantUserRecord(
                        fixture.actorAppUserId,
                        fixture.tenantId,
                        null,
                        "doctor@example.com",
                        "dr.verma",
                        null,
                        "Dr Amit Verma",
                        "ACTIVE",
                        "DOCTOR",
                        "ACTIVE",
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        "ACTIVE"
                )
        ));
        PrescriptionSafetyReviewEntity review = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, "DUPLICATE_INTENTIONAL", "Duplicate intentional"
                ))),
                null,
                null,
                null
        );
        review.markFinalized();
        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId())).thenReturn(java.util.Optional.of(review));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId, fixture.prescription.getId());
        MedicationSafetyEvaluationResult evaluation = medicationSafetyReviewService.getEvaluation(fixture.tenantId, fixture.consultationId, fixture.prescription.getId());

        assertThat(response.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.FINALIZED.name());
        assertThat(response.reviewedByDisplayName()).isEqualTo("Dr Amit Verma");
        assertThat(response.reviewedByAppUserId()).isEqualTo(fixture.actorAppUserId);
        assertThat(response.findingReviews()).hasSize(1);
        assertThat(response.findingReviews().get(0).acknowledged()).isTrue();
        assertThat(response.findingReviews().get(0).reasonCode()).isEqualTo("DUPLICATE_INTENTIONAL");
        assertThat(response.findingReviews().get(0).reasonText()).isEqualTo("Duplicate intentional");
        assertThat(evaluation.evaluationId()).isEqualTo(fixture.current.evaluationId());
        assertThat(evaluation.findings()).hasSize(1);
        assertThat(evaluation.findings().get(0).findingId()).isEqualTo(fixture.finding.findingId());
    }

    @Test
    void finalizedReviewPrefersExactSnapshotOverNewerShadowRow() throws Exception {
        SafetyFixture fixture = fixtureWithWarningFinding();
        when(medicationSafetyService.buildEvaluationContext(fixture.tenantId, fixture.consultationId)).thenReturn(fixture.context);
        PrescriptionSafetyReviewEntity acknowledgedReview = PrescriptionSafetyReviewEntity.create(
                fixture.tenantId,
                fixture.patientId,
                fixture.consultationId,
                fixture.prescription.getId(),
                fixture.prescription.getVersionNumber(),
                fixture.context.prescriptionHash(),
                fixture.context.patientContextHash(),
                fixture.current.rulesVersion(),
                fixture.current.evaluationId(),
                fixture.current.overallSeverity(),
                objectMapper.writeValueAsString(fixture.current),
                MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED.name(),
                fixture.actorAppUserId,
                objectMapper.writeValueAsString(List.of(new MedicationSafetyFindingReviewDecision(
                        fixture.finding.findingId(), fixture.finding.ruleCode(), true, false, "DUPLICATE_INTENTIONAL", "Duplicate intentional"
                ))),
                null,
                null,
                null
        );
        acknowledgedReview.markFinalized();

        when(reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(fixture.tenantId, fixture.prescription.getId()))
                .thenReturn(java.util.Optional.of(acknowledgedReview));

        MedicationSafetyReviewResponse response = medicationSafetyReviewService.getReview(fixture.tenantId, fixture.consultationId, fixture.prescription.getId());

        assertThat(response.reviewId()).isEqualTo(acknowledgedReview.getId());
        assertThat(response.decisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.FINALIZED.name());
        assertThat(response.findingReviews()).hasSize(1);
        assertThat(response.findingReviews().get(0).acknowledged()).isTrue();
        assertThat(response.findingReviews().get(0).reasonCode()).isEqualTo("DUPLICATE_INTENTIONAL");
        assertThat(response.findingReviews().get(0).reasonText()).isEqualTo("Duplicate intentional");
    }

    private SafetyFixture fixtureWithWarningFinding() {
        return new SafetyFixture("f-warning", MedicationSafetySeverity.WARNING, MedicationSafetyFindingCategory.DUPLICATE_INGREDIENT);
    }

    private SafetyFixture fixtureWithCriticalFinding() {
        return new SafetyFixture("f-critical", MedicationSafetySeverity.CRITICAL, MedicationSafetyFindingCategory.ALLERGY_CONFLICT);
    }

    private final class SafetyFixture {
        final UUID tenantId = UUID.randomUUID();
        final UUID consultationId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final UUID doctorUserId = UUID.randomUUID();
        final UUID actorAppUserId = UUID.randomUUID();
        final ConsultationEntity consultation;
        final PatientEntity patient;
        final PrescriptionEntity prescription;
        final MedicationSafetyFinding finding;
        final MedicationSafetyEvaluationResult current;
        final MedicationSafetyEvaluationContext context;

        SafetyFixture(String findingId, MedicationSafetySeverity severity, MedicationSafetyFindingCategory category) {
            this.consultation = ConsultationEntity.create(tenantId, patientId, doctorUserId, null);
            this.patient = PatientEntity.create(tenantId, "PAT-1");
            this.prescription = PrescriptionEntity.create(tenantId, patientId, doctorUserId, consultationId, null, "RX-1");
            this.prescription.update("Dx", "Advice", null);
            this.finding = new MedicationSafetyFinding(
                    findingId,
                    "RULE-" + findingId,
                    category,
                    severity,
                    category == MedicationSafetyFindingCategory.ALLERGY_CONFLICT ? "Allergy conflict" : "Duplicate active ingredient",
                    "Finding summary",
                    "Rationale",
                    List.of("med-1"),
                    List.of("Paracetamol"),
                    List.of("Evidence"),
                    List.of("Source"),
                    "PENDING_VERIFICATION",
                    severity != MedicationSafetySeverity.CRITICAL,
                    severity == MedicationSafetySeverity.CRITICAL,
                    "Review",
                    List.of()
            );
            this.current = new MedicationSafetyEvaluationResult(
                    "eval-" + findingId,
                    OffsetDateTime.now(),
                    prescription.getId(),
                    severity,
                    List.of(finding),
                    List.of(),
                    new MedicationSafetyCoverage(true, true, true, true, true, true, true, true, true, true, "PARTIAL"),
                    "med-safety-v1",
                    new MedicationSafetyEvaluationResult.SourceSnapshotMetadata(tenantId, patientId, consultationId, prescription.getId(), PrescriptionStatus.DRAFT.name())
            );
            this.context = new MedicationSafetyEvaluationContext(
                    consultation,
                    patient,
                    prescription,
                    new ClinicalContextResponse(
                            tenantId,
                            patientId,
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
                    ),
                    new MedicationSafetyEvaluationRequest(
                            tenantId,
                            patientId,
                            consultationId,
                            prescription.getId(),
                            PrescriptionStatus.DRAFT.name(),
                            List.of(),
                            List.of(),
                            null,
                            List.of(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            java.util.Map.of()
                    ),
                    "prescription-hash",
                    "patient-context-hash",
                    "snapshot-hash"
            );
        }
    }
}
