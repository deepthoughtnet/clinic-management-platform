package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewEntity;
import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewRepository;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationSafetyReviewService {
    private static final Logger log = LoggerFactory.getLogger(MedicationSafetyReviewService.class);

    private final MedicationSafetyService medicationSafetyService;
    private final MedicationSafetyEngine medicationSafetyEngine;
    private final PrescriptionSafetyReviewRepository reviewRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final PermissionChecker permissionChecker;
    private final TenantUserManagementService tenantUserManagementService;
    private final ObjectMapper objectMapper;

    public MedicationSafetyReviewService(MedicationSafetyService medicationSafetyService,
                                         MedicationSafetyEngine medicationSafetyEngine,
                                         PrescriptionSafetyReviewRepository reviewRepository,
                                         AuditEventPublisher auditEventPublisher,
                                         PermissionChecker permissionChecker,
                                         TenantUserManagementService tenantUserManagementService,
                                         ObjectMapper objectMapper) {
        this.medicationSafetyService = medicationSafetyService;
        this.medicationSafetyEngine = medicationSafetyEngine;
        this.reviewRepository = reviewRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.permissionChecker = permissionChecker;
        this.tenantUserManagementService = tenantUserManagementService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MedicationSafetyReviewResponse getReview(UUID tenantId, UUID consultationId) {
        return getReview(tenantId, consultationId, null);
    }

    @Transactional(readOnly = true)
    public MedicationSafetyReviewResponse getReview(UUID tenantId, UUID consultationId, UUID prescriptionId) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        PrescriptionSafetyReviewEntity review = latestReview(tenantId, context, prescriptionId);
        boolean useStoredSnapshot = review != null && (
                MedicationSafetyReviewDecisionStatus.FINALIZED.name().equals(review.getDecisionStatus())
                        || isHistoricalPrescriptionQuery(context, prescriptionId)
        );
        MedicationSafetyEvaluationResult current = useStoredSnapshot ? loadEvaluationSnapshot(review) : medicationSafetyEngine.evaluate(context.request());
        if (current == null) {
            current = medicationSafetyEngine.evaluate(context.request());
        }
        boolean stale = review != null && !useStoredSnapshot && isStale(review, context);
        return toResponse(review, context, current, stale, useStoredSnapshot);
    }

    @Transactional(readOnly = true)
    public MedicationSafetyEvaluationResult getEvaluation(UUID tenantId, UUID consultationId, UUID prescriptionId) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        PrescriptionSafetyReviewEntity review = latestReview(tenantId, context, prescriptionId);
        boolean useStoredSnapshot = review != null && (
                MedicationSafetyReviewDecisionStatus.FINALIZED.name().equals(review.getDecisionStatus())
                        || isHistoricalPrescriptionQuery(context, prescriptionId)
        );
        MedicationSafetyEvaluationResult current = useStoredSnapshot ? loadEvaluationSnapshot(review) : medicationSafetyEngine.evaluate(context.request());
        return current == null ? medicationSafetyEngine.evaluate(context.request()) : current;
    }

    @Transactional
    public MedicationSafetyReviewResponse captureSnapshot(UUID tenantId, UUID consultationId, UUID actorAppUserId, MedicationSafetyEvaluationResult current) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        MedicationSafetyEvaluationResult effectiveCurrent = current == null ? medicationSafetyEngine.evaluate(context.request()) : current;
        lockActivePrescription(tenantId, context);
        PrescriptionSafetyReviewEntity existing = latestReview(tenantId, context.prescription() == null ? null : context.prescription().getId());
        if (existing != null && snapshotMatches(existing, context) && !isStale(existing, context)) {
            return toResponse(existing, context, effectiveCurrent, false, false);
        }

        String evaluationJson = serialize(effectiveCurrent);
        boolean noActionableFindings = effectiveCurrent.findings() == null || effectiveCurrent.findings().stream().noneMatch(finding -> finding != null && (finding.severity() == MedicationSafetySeverity.WARNING || finding.severity() == MedicationSafetySeverity.CRITICAL));
        Integer snapshotGeneration = nextSnapshotGeneration(existing);
        PrescriptionSafetyReviewEntity entity = PrescriptionSafetyReviewEntity.create(
                tenantId,
                context.patient().getId(),
                context.consultation().getId(),
                context.prescription().getId(),
                context.prescription().getVersionNumber(),
                context.prescriptionHash(),
                context.patientContextHash(),
                effectiveCurrent.rulesVersion(),
                effectiveCurrent.evaluationId(),
                effectiveCurrent.overallSeverity(),
                evaluationJson,
                noActionableFindings ? MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name() : MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name(),
                null,
                null,
                null,
                null,
                null,
                snapshotGeneration
        );
        PrescriptionSafetyReviewEntity saved = reviewRepository.save(entity);
        audit(tenantId, context.consultation().getId(), context.prescription() == null ? null : context.prescription().getId(), actorAppUserId,
                "prescription.safety.snapshot",
                "Medication safety snapshot captured",
                Map.of(
                        "rulesVersion", effectiveCurrent.rulesVersion(),
                        "findings", effectiveCurrent.findings() == null ? 0 : effectiveCurrent.findings().size(),
                        "decisionStatus", saved.getDecisionStatus()
                ));
        return toResponse(saved, context, effectiveCurrent, false, false);
    }

    @Transactional
    public MedicationSafetyEvaluationResult evaluateAndPersist(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        MedicationSafetyEvaluationResult current = medicationSafetyService.evaluateForConsultation(tenantId, consultationId, actorAppUserId);
        captureSnapshot(tenantId, consultationId, actorAppUserId, current);
        return current;
    }

    @Transactional
    public MedicationSafetyReviewResponse runSafetyCheck(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        MedicationSafetyEvaluationResult current = medicationSafetyService.evaluateForConsultation(tenantId, consultationId, actorAppUserId);
        return captureSnapshot(tenantId, consultationId, actorAppUserId, current);
    }

    @Transactional
    public MedicationSafetyReviewResponse submitReview(UUID tenantId, UUID consultationId, UUID actorAppUserId, MedicationSafetyReviewRequest request) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        MedicationSafetyEvaluationResult current = medicationSafetyEngine.evaluate(context.request());
        validateSnapshot(request, context, current);

        List<MedicationSafetyFindingReviewDecision> decisions = request == null || request.findings() == null ? List.of() : request.findings();
        ReviewPolicyOutcome policyOutcome = evaluatePolicy(current, decisions);
        if (hasCriticalOverrideWithoutReason(decisions)) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.BAD_REQUEST,
                    "SAFETY_CRITICAL_OVERRIDE_REQUIRED",
                    "A critical override reason is required.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    current == null || current.overallSeverity() == null ? null : current.overallSeverity().name(),
                    "OVERRIDE_CRITICAL",
                    findingIds(actionableFindings(current))
            );
        }
        if (policyOutcome.unauthorizedCriticalOverride > 0) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.FORBIDDEN,
                    "SAFETY_OVERRIDE_NOT_AUTHORIZED",
                    "You are not authorized to override the current critical medication safety finding.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    current == null || current.overallSeverity() == null ? null : current.overallSeverity().name(),
                    "OVERRIDE_CRITICAL",
                    findingIds(actionableFindings(current))
            );
        }
        PrescriptionSafetyReviewEntity review = saveReviewEntity(tenantId, context, current, decisions, actorAppUserId, policyOutcome);
        MedicationSafetyReviewResponse response = toResponse(review, context, current, false, false);
        audit(tenantId, context.consultation().getId(), context.prescription() == null ? null : context.prescription().getId(), actorAppUserId, "prescription.safety.reviewed",
                "Medication safety review saved", Map.of(
                        "rulesVersion", current.rulesVersion(),
                        "findings", current.findings() == null ? 0 : current.findings().size(),
                        "warnings", policyOutcome.warningCount,
                        "critical", policyOutcome.criticalCount,
                        "decisionStatus", review.getDecisionStatus()
                ));
        return response;
    }

    @Transactional(readOnly = true)
    public void assertFinalizationReady(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        MedicationSafetyEvaluationResult current = medicationSafetyEngine.evaluate(context.request());
        List<MedicationSafetyFinding> actionableFindings = actionableFindings(current);
        PrescriptionSafetyReviewEntity review = latestReview(tenantId, context.prescription() == null ? null : context.prescription().getId());

        if (actionableFindings.isEmpty()) {
            return;
        }
        if (review == null) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_REVIEW_REQUIRED",
                    "Medication safety findings must be reviewed before finalizing.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "CURRENT",
                    requiredAction(actionableFindings),
                    findingIds(actionableFindings)
            );
        }
        if (isStale(review, context)) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_EVALUATION_STALE",
                    "Prescription changed after safety review. Run the safety check again.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "STALE",
                    "RERUN_SAFETY_REVIEW",
                    findingIds(actionableFindings)
            );
        }
        ReviewPolicyOutcome policyOutcome = evaluatePolicy(current, parseDecisions(review.getAcknowledgementJson()));
        if (policyOutcome.missingWarningAcknowledgements > 0) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_WARNING_ACKNOWLEDGEMENT_REQUIRED",
                    "Medication safety warnings must be acknowledged before finalizing.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    review.getDecisionStatus(),
                    "ACKNOWLEDGE_WARNINGS",
                    findingIds(actionableFindings)
            );
        }
        if (policyOutcome.missingCriticalOverrides > 0) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_CRITICAL_OVERRIDE_REQUIRED",
                    "Critical medication safety findings require an authorized override reason before finalizing.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    review.getDecisionStatus(),
                    "OVERRIDE_CRITICAL",
                    findingIds(actionableFindings)
            );
        }
        if (policyOutcome.unauthorizedCriticalOverride > 0) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.FORBIDDEN,
                    "SAFETY_OVERRIDE_NOT_AUTHORIZED",
                    "You are not authorized to override the current critical medication safety finding.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    review.getDecisionStatus(),
                    "OVERRIDE_CRITICAL",
                    findingIds(actionableFindings)
            );
        }
    }

    @Transactional
    public void markFinalized(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        MedicationSafetyEvaluationContext context = medicationSafetyService.buildEvaluationContext(tenantId, consultationId);
        MedicationSafetyEvaluationResult current = medicationSafetyEngine.evaluate(context.request());
        lockActivePrescription(tenantId, context);
        PrescriptionSafetyReviewEntity review = latestReview(tenantId, context.prescription() == null ? null : context.prescription().getId());
        if (review == null || isStale(review, context)) {
            review = ensureSummaryReview(tenantId, context, current, actorAppUserId);
        }
        review.markFinalized();
        reviewRepository.save(review);
        audit(tenantId, context.consultation().getId(), context.prescription() == null ? null : context.prescription().getId(), actorAppUserId, "prescription.safety.finalized",
                "Medication safety review finalized with prescription", Map.of(
                        "rulesVersion", current.rulesVersion(),
                        "findings", current.findings() == null ? 0 : current.findings().size(),
                        "decisionStatus", review.getDecisionStatus()
                ));
    }

    private PrescriptionSafetyReviewEntity ensureSummaryReview(UUID tenantId,
                                                               MedicationSafetyEvaluationContext context,
                                                               MedicationSafetyEvaluationResult current,
                                                               UUID actorAppUserId) {
        lockActivePrescription(tenantId, context);
        PrescriptionSafetyReviewEntity review = latestReview(tenantId, context.prescription() == null ? null : context.prescription().getId());
        if (review != null && !isStale(review, context)) {
            return review;
        }
        String evaluationJson = serialize(current);
        String decisionStatus = current.findings() == null || current.findings().isEmpty()
                ? MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name()
                : MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS.name();
        PrescriptionSafetyReviewEntity entity = PrescriptionSafetyReviewEntity.create(
                tenantId,
                context.patient().getId(),
                context.consultation().getId(),
                context.prescription().getId(),
                context.prescription().getVersionNumber(),
                context.prescriptionHash(),
                context.patientContextHash(),
                current.rulesVersion(),
                current.evaluationId(),
                current.overallSeverity(),
                evaluationJson,
                decisionStatus,
                actorAppUserId,
                null,
                null,
                null,
                null,
                nextSnapshotGeneration(review)
        );
        return reviewRepository.save(entity);
    }

    private PrescriptionSafetyReviewEntity saveReviewEntity(UUID tenantId,
                                                            MedicationSafetyEvaluationContext context,
                                                            MedicationSafetyEvaluationResult current,
                                                            List<MedicationSafetyFindingReviewDecision> decisions,
                                                            UUID actorAppUserId,
                                                            ReviewPolicyOutcome policyOutcome) {
        lockActivePrescription(tenantId, context);
        String evaluationJson = serialize(current);
        String acknowledgementJson = serialize(decisions);
        String decisionStatus = policyOutcome.decisionStatus.name();
        String overrideReasonCode = policyOutcome.firstCriticalOverrideReasonCode;
        String overrideReasonText = policyOutcome.firstCriticalOverrideReasonText;
        String overrideCategory = policyOutcome.firstCriticalOverrideCategory;
        PrescriptionSafetyReviewEntity entity = latestReview(tenantId, context.prescription() == null ? null : context.prescription().getId());
        if (entity == null || isStale(entity, context)) {
            entity = PrescriptionSafetyReviewEntity.create(
                    tenantId,
                    context.patient().getId(),
                    context.consultation().getId(),
                    context.prescription().getId(),
                    context.prescription().getVersionNumber(),
                    context.prescriptionHash(),
                    context.patientContextHash(),
                    current.rulesVersion(),
                    current.evaluationId(),
                    current.overallSeverity(),
                    evaluationJson,
                    decisionStatus,
                    actorAppUserId,
                    acknowledgementJson,
                    overrideReasonCode,
                    overrideReasonText,
                    overrideCategory,
                    nextSnapshotGeneration(entity)
            );
        } else {
            entity.update(evaluationJson, decisionStatus, actorAppUserId, acknowledgementJson, overrideReasonCode, overrideReasonText, overrideCategory);
        }
        return reviewRepository.save(entity);
    }

    private ReviewPolicyOutcome evaluatePolicy(MedicationSafetyEvaluationResult current, List<MedicationSafetyFindingReviewDecision> decisions) {
        List<MedicationSafetyFinding> findings = current == null || current.findings() == null ? List.of() : current.findings();
        Map<String, MedicationSafetyFindingReviewDecision> byFindingId = new LinkedHashMap<>();
        if (decisions != null) {
            for (MedicationSafetyFindingReviewDecision decision : decisions) {
                if (decision != null && decision.findingId() != null) {
                    byFindingId.putIfAbsent(decision.findingId(), decision);
                }
            }
        }

        int warningCount = 0;
        int criticalCount = 0;
        int missingWarningAcknowledgements = 0;
        int missingCriticalOverrides = 0;
        int unauthorizedCriticalOverride = 0;
        String firstCriticalOverrideReasonCode = null;
        String firstCriticalOverrideReasonText = null;
        String firstCriticalOverrideCategory = null;

        for (MedicationSafetyFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            MedicationSafetyFindingReviewDecision decision = byFindingId.get(finding.findingId());
            if (finding.severity() == MedicationSafetySeverity.WARNING) {
                warningCount++;
                if (decision == null || !decision.acknowledged()) {
                    missingWarningAcknowledgements++;
                }
            }
            if (finding.severity() == MedicationSafetySeverity.CRITICAL) {
                criticalCount++;
                if (decision == null || !decision.overrideApplied()) {
                    missingCriticalOverrides++;
                } else {
                    if (!canOverrideCritical()) {
                        unauthorizedCriticalOverride++;
                    }
                    if (firstCriticalOverrideReasonCode == null) {
                        firstCriticalOverrideReasonCode = trimToNull(decision.reasonCode());
                        firstCriticalOverrideReasonText = trimToNull(decision.reasonText());
                        firstCriticalOverrideCategory = finding.category() == null ? null : finding.category().name();
                    }
                    if (!hasText(decision.reasonText())) {
                        missingCriticalOverrides++;
                    }
                }
            }
        }

        MedicationSafetyReviewDecisionStatus status = criticalCount > 0
                ? (missingCriticalOverrides > 0 ? MedicationSafetyReviewDecisionStatus.NOT_REVIEWED : MedicationSafetyReviewDecisionStatus.CRITICAL_OVERRIDE_APPROVED)
                : (warningCount > 0
                    ? (missingWarningAcknowledgements > 0 ? MedicationSafetyReviewDecisionStatus.NOT_REVIEWED : MedicationSafetyReviewDecisionStatus.WARNINGS_ACKNOWLEDGED)
                    : MedicationSafetyReviewDecisionStatus.REVIEWED_NO_BLOCKING_FINDINGS);

        return new ReviewPolicyOutcome(status, warningCount, criticalCount, missingWarningAcknowledgements, missingCriticalOverrides, unauthorizedCriticalOverride, firstCriticalOverrideReasonCode, firstCriticalOverrideReasonText, firstCriticalOverrideCategory);
    }

    private List<MedicationSafetyFindingReviewDecision> parseDecisions(String acknowledgementJson) {
        if (!hasText(acknowledgementJson)) {
            return List.of();
        }
        try {
            MedicationSafetyFindingReviewDecision[] decisions = objectMapper.readValue(acknowledgementJson, MedicationSafetyFindingReviewDecision[].class);
            return decisions == null ? List.of() : List.of(decisions);
        } catch (Exception ex) {
            log.warn("Unable to parse medication safety acknowledgement JSON", ex);
            return List.of();
        }
    }

    private List<MedicationSafetyFinding> actionableFindings(MedicationSafetyEvaluationResult current) {
        if (current == null || current.findings() == null) {
            return List.of();
        }
        return current.findings().stream()
                .filter(finding -> finding != null && (finding.severity() == MedicationSafetySeverity.WARNING || finding.severity() == MedicationSafetySeverity.CRITICAL))
                .toList();
    }

    private void validateSnapshot(MedicationSafetyReviewRequest request, MedicationSafetyEvaluationContext context, MedicationSafetyEvaluationResult current) {
        if (request == null) {
            return;
        }
        if (hasText(request.evaluationId()) && !request.evaluationId().equals(current.evaluationId())) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_REVIEW_VERSION_MISMATCH",
                    "The medication safety review does not match the current evaluation.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "CURRENT",
                    "RERUN_SAFETY_REVIEW",
                    findingIds(actionableFindings(current))
            );
        }
        if (hasText(request.prescriptionHash()) && !request.prescriptionHash().equals(context.prescriptionHash())) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_EVALUATION_STALE",
                    "Prescription changed after safety review. Run the safety check again.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "STALE",
                    "RERUN_SAFETY_REVIEW",
                    findingIds(actionableFindings(current))
            );
        }
        if (hasText(request.patientContextHash()) && !request.patientContextHash().equals(context.patientContextHash())) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_EVALUATION_STALE",
                    "Patient context changed after safety review. Run the safety check again.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "STALE",
                    "RERUN_SAFETY_REVIEW",
                    findingIds(actionableFindings(current))
            );
        }
        if (hasText(request.rulesVersion()) && !request.rulesVersion().equals(current.rulesVersion())) {
            throw new MedicationSafetyGuardException(
                    HttpStatus.CONFLICT,
                    "SAFETY_REVIEW_VERSION_MISMATCH",
                    "Medication safety rules changed after the review was prepared.",
                    context.prescription() == null ? null : context.prescription().getId(),
                    "CURRENT",
                    "RERUN_SAFETY_REVIEW",
                    findingIds(actionableFindings(current))
            );
        }
    }

    private boolean isStale(PrescriptionSafetyReviewEntity review, MedicationSafetyEvaluationContext context) {
        if (review == null || context == null) {
            return false;
        }
        return !review.getPrescriptionHash().equals(context.prescriptionHash())
                || !review.getPatientContextHash().equals(context.patientContextHash())
                || !review.getRulesVersion().equals(medicationSafetyEngine.rulesVersion());
    }

    private boolean isHistoricalPrescriptionQuery(MedicationSafetyEvaluationContext context, UUID prescriptionId) {
        return prescriptionId != null
                && context != null
                && context.prescription() != null
                && !prescriptionId.equals(context.prescription().getId());
    }

    private MedicationSafetyReviewResponse toResponse(PrescriptionSafetyReviewEntity review,
                                                      MedicationSafetyEvaluationContext context,
                                                      MedicationSafetyEvaluationResult current,
                                                      boolean stale,
                                                      boolean useStoredSnapshot) {
        List<MedicationSafetyFindingReviewStatus> statuses = new ArrayList<>();
        List<MedicationSafetyFinding> findings = current == null || current.findings() == null ? List.of() : current.findings();
        Map<String, MedicationSafetyFindingReviewDecision> decisions = new LinkedHashMap<>();
        if (review != null && hasText(review.getAcknowledgementJson())) {
            for (MedicationSafetyFindingReviewDecision decision : parseDecisions(review.getAcknowledgementJson())) {
                if (decision != null && decision.findingId() != null) {
                    decisions.put(decision.findingId(), decision);
                }
            }
        }
        int warningCount = 0;
        int criticalCount = 0;
        boolean finalized = review != null && MedicationSafetyReviewDecisionStatus.FINALIZED.name().equals(review.getDecisionStatus());
        for (MedicationSafetyFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            MedicationSafetyFindingReviewDecision decision = decisions.get(finding.findingId());
            boolean ackRequired = finding.severity() == MedicationSafetySeverity.WARNING;
            boolean overrideRequired = finding.severity() == MedicationSafetySeverity.CRITICAL;
            if (finding.severity() == MedicationSafetySeverity.WARNING) {
                warningCount++;
            }
            if (finding.severity() == MedicationSafetySeverity.CRITICAL) {
                criticalCount++;
            }
            statuses.add(new MedicationSafetyFindingReviewStatus(
                    finding.findingId(),
                    finding.ruleCode(),
                    finding.title(),
                    finding.category() == null ? null : finding.category().name(),
                    finding.severity() == null ? null : finding.severity().name(),
                    ackRequired,
                    overrideRequired,
                    decision != null && decision.acknowledged(),
                    decision != null && decision.overrideApplied(),
                    decision == null ? null : trimToNull(decision.reasonCode()),
                    decision == null ? null : trimToNull(decision.reasonText())
            ));
        }
        boolean noActionableFindings = warningCount + criticalCount == 0;
        boolean hasPersistedReview = review != null;
        boolean ready = hasPersistedReview && !stale && !finalized && !useStoredSnapshot && (noActionableFindings
                || (review != null && review.getDecisionStatus() != null && !MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name().equals(review.getDecisionStatus())));
        String requiredAction = finalized
                ? "FINALIZED"
                : stale
                ? "RERUN_SAFETY_CHECK"
                : ready
                ? "NONE"
                : (criticalCount > 0 ? "OVERRIDE_CRITICAL" : (warningCount > 0 ? "ACKNOWLEDGE_WARNINGS" : "RUN_SAFETY_REVIEW"));
        String reviewedByDisplayName = review == null ? null : reviewedByDisplayName(review.getReviewedByAppUserId(), review.getTenantId());
        return new MedicationSafetyReviewResponse(
                review == null ? null : review.getId(),
                context == null ? null : context.consultation().getId(),
                context == null || context.prescription() == null ? null : context.prescription().getId(),
                context == null ? null : context.patient().getId(),
                current == null ? null : current.evaluationId(),
                context == null ? null : context.prescriptionHash(),
                context == null ? null : context.patientContextHash(),
                current == null ? null : current.rulesVersion(),
                review == null ? MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name() : review.getDecisionStatus(),
                stale,
                ready,
                requiredAction,
                review == null ? null : review.getReviewedAt(),
                review == null ? null : review.getReviewedByAppUserId(),
                reviewedByDisplayName,
                review == null ? null : review.getFinalizedAt(),
                current == null || current.overallSeverity() == null ? null : current.overallSeverity().name(),
                warningCount + criticalCount,
                warningCount,
                criticalCount,
                statuses,
                current == null ? List.of() : current.dataQualityWarnings()
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize medication safety review snapshot", ex);
        }
    }

    private void audit(UUID tenantId, UUID consultationId, UUID prescriptionId, UUID actorAppUserId, String action, String summary, Map<String, Object> details) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                "PRESCRIPTION_SAFETY_REVIEW",
                prescriptionId,
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                summary,
                serialize(details == null ? Map.of() : details)
        ));
        log.info(
                "[MED-SAFETY-REVIEW-AUDIT] tenantId={} consultationId={} prescriptionId={} action={} summary={}",
                tenantId,
                consultationId,
                prescriptionId,
                action,
                summary
        );
    }

    private PrescriptionSafetyReviewEntity latestReview(UUID tenantId, UUID prescriptionId) {
        if (prescriptionId == null) {
            return null;
        }
        return reviewRepository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(tenantId, prescriptionId).orElse(null);
    }

    private PrescriptionSafetyReviewEntity latestReview(UUID tenantId, MedicationSafetyEvaluationContext context, UUID prescriptionId) {
        UUID resolvedPrescriptionId = prescriptionId != null
                ? prescriptionId
                : context == null || context.prescription() == null ? null : context.prescription().getId();
        if (resolvedPrescriptionId == null) {
            return null;
        }
        return latestReview(tenantId, resolvedPrescriptionId);
    }

    private void lockActivePrescription(UUID tenantId, MedicationSafetyEvaluationContext context) {
        if (context == null || context.prescription() == null) {
            return;
        }
        medicationSafetyService.lockPrescriptionForSafety(tenantId, context.prescription().getId());
    }

    private boolean snapshotMatches(PrescriptionSafetyReviewEntity review, MedicationSafetyEvaluationContext context) {
        if (review == null || context == null) {
            return false;
        }
        return hasText(review.getPrescriptionHash())
                && hasText(review.getPatientContextHash())
                && hasText(review.getRulesVersion())
                && review.getPrescriptionHash().equals(context.prescriptionHash())
                && review.getPatientContextHash().equals(context.patientContextHash())
                && review.getRulesVersion().equals(medicationSafetyEngine.rulesVersion());
    }

    private Integer nextSnapshotGeneration(PrescriptionSafetyReviewEntity review) {
        if (review == null || review.getSnapshotGeneration() == null) {
            return 1;
        }
        return review.getSnapshotGeneration() + 1;
    }

    private boolean canOverrideCritical() {
        return permissionChecker.hasAnyRole(Roles.DOCTOR, Roles.CLINIC_ADMIN, Roles.TENANT_ADMIN, Roles.PLATFORM_ADMIN);
    }

    private boolean hasCriticalOverrideWithoutReason(List<MedicationSafetyFindingReviewDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return false;
        }
        for (MedicationSafetyFindingReviewDecision decision : decisions) {
            if (decision != null && decision.overrideApplied() && !hasText(decision.reasonText())) {
                return true;
            }
        }
        return false;
    }

    private List<String> findingIds(List<MedicationSafetyFinding> findings) {
        if (findings == null) {
            return List.of();
        }
        return findings.stream().filter(finding -> finding != null && finding.findingId() != null).map(MedicationSafetyFinding::findingId).toList();
    }

    private String requiredAction(List<MedicationSafetyFinding> findings) {
        boolean hasCritical = findings != null && findings.stream().anyMatch(finding -> finding != null && finding.severity() == MedicationSafetySeverity.CRITICAL);
        boolean hasWarning = findings != null && findings.stream().anyMatch(finding -> finding != null && finding.severity() == MedicationSafetySeverity.WARNING);
        if (hasCritical) {
            return "OVERRIDE_CRITICAL";
        }
        if (hasWarning) {
            return "ACKNOWLEDGE_WARNINGS";
        }
        return "RUN_SAFETY_REVIEW";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private MedicationSafetyEvaluationResult loadEvaluationSnapshot(PrescriptionSafetyReviewEntity review) {
        if (review == null || !hasText(review.getEvaluationSnapshotJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(review.getEvaluationSnapshotJson(), MedicationSafetyEvaluationResult.class);
        } catch (Exception ex) {
            log.warn("Unable to parse finalized medication safety evaluation snapshot", ex);
            return null;
        }
    }

    private String reviewedByDisplayName(UUID reviewedByAppUserId, UUID tenantId) {
        if (reviewedByAppUserId == null || tenantId == null) {
            return "User unavailable";
        }
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> reviewedByAppUserId.equals(user.appUserId()))
                .map(this::displayName)
                .findFirst()
                .orElse("User unavailable");
    }

    private String displayName(TenantUserRecord user) {
        if (user == null) {
            return "User unavailable";
        }
        if (hasText(user.displayName())) {
            return user.displayName().trim();
        }
        if (hasText(user.username())) {
            return user.username().trim();
        }
        if (hasText(user.email())) {
            return user.email().trim();
        }
        return "User unavailable";
    }

    private record ReviewPolicyOutcome(
            MedicationSafetyReviewDecisionStatus decisionStatus,
            int warningCount,
            int criticalCount,
            int missingWarningAcknowledgements,
            int missingCriticalOverrides,
            int unauthorizedCriticalOverride,
            String firstCriticalOverrideReasonCode,
            String firstCriticalOverrideReasonText,
            String firstCriticalOverrideCategory
    ) {
    }
}
