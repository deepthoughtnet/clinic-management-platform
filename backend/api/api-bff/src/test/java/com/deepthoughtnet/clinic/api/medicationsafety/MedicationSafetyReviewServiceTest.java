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
        assertThat(response.requiredAction()).isEqualTo("ACKNOWLEDGE_WARNINGS");
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
