package com.deepthoughtnet.clinic.discover.publicprofilemoderation;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationDecisionRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationQueueRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationSubmissionRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfilePublicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileReviewFindingRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileSubmissionEligibilityRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderPublicProfileModerationService {
    private static final String SOURCE_SYSTEM = "PROVIDER_PUBLIC_PROFILE_DRAFT";

    private final ProviderPublicProfileDraftService draftService;
    private final ProviderOwnershipService ownershipService;
    private final ProviderPublicProfileService publicProfileService;
    private final DiscoverPublicProfileSubmissionRepository submissions;
    private final DiscoverPublicProfileReviewFindingRepository findings;
    private final DiscoverPublicProfilePublicationRepository publications;
    private final ObjectMapper objectMapper;

    public ProviderPublicProfileModerationService(
            ProviderPublicProfileDraftService draftService,
            ProviderOwnershipService ownershipService,
            ProviderPublicProfileService publicProfileService,
            DiscoverPublicProfileSubmissionRepository submissions,
            DiscoverPublicProfileReviewFindingRepository findings,
            DiscoverPublicProfilePublicationRepository publications,
            ObjectMapper objectMapper
    ) {
        this.draftService = draftService;
        this.ownershipService = ownershipService;
        this.publicProfileService = publicProfileService;
        this.submissions = submissions;
        this.findings = findings;
        this.publications = publications;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Transactional(readOnly = true)
    public PublicProfileSubmissionEligibilityRecord submissionEligibility(UUID providerAccountId, String publicProfileReference, boolean tenantConsentEnabled) {
        PublicProfileDraftWorkspaceRecord draft = draftService.getDraft(providerAccountId, publicProfileReference);
        DiscoverPublicProfileSubmissionEntity currentSubmission = findLatestSubmission(publicProfileReference).orElse(null);
        List<String> blockers = new ArrayList<>();
        if (draft == null || providerAccountId == null) {
            blockers.add("OWNERSHIP_NOT_VERIFIED");
        } else {
            OwnershipRecord ownership = ownershipService.findOwnership(providerAccountId, publicProfileReference).orElse(null);
            if (ownership == null || ownership.status() != PublicProfileOwnershipStatus.VERIFIED) {
                blockers.add("OWNERSHIP_NOT_VERIFIED");
            }
        }
        if (draft == null || draft.readiness() == null || !draft.readiness().ready()) {
            blockers.add("PROFILE_INCOMPLETE");
        }
        if (!tenantConsentEnabled) {
            blockers.add("TENANT_CONSENT_REQUIRED");
        }
        if (currentSubmission != null && isBlockingSubmissionStatus(currentSubmission.getModerationStatus())) {
            blockers.add("ACTIVE_SUBMISSION_EXISTS");
        }
        List<String> actions = allowedSubmissionActions(blockers, draft, currentSubmission);
        return new PublicProfileSubmissionEligibilityRecord(
                blockers.isEmpty(),
                blockers.stream().distinct().toList(),
                actions,
                currentSubmission == null ? "NOT_SUBMITTED" : currentSubmission.getModerationStatus(),
                currentSubmission == null ? "UNPUBLISHED" : currentSubmission.getPublicationStatusSnapshot(),
                currentSubmission == null ? null : currentSubmission.getSubmissionReference(),
                currentSubmission == null ? null : currentSubmission.getSubmittedDraftVersion(),
                currentSubmission == null ? null : currentSubmission.getSubmittedAt(),
                currentSubmission == null ? null : currentSubmission.getDecisionAt(),
                draft == null ? 0 : draft.currentVersion()
        );
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> findSubmission(String publicProfileReference) {
        return findCurrentSubmission(publicProfileReference).map(this::toSubmissionRecord);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> findSubmissionByReference(String submissionReference) {
        if (!StringUtils.hasText(submissionReference)) {
            return Optional.empty();
        }
        return submissions.findBySubmissionReference(submissionReference.trim()).map(this::toSubmissionRecord);
    }

    @Transactional(readOnly = true)
    public List<PublicProfileModerationSubmissionRecord> listSubmissions(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return List.of();
        }
        return submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(publicProfileReference.trim()).stream()
                .map(this::toSubmissionRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicProfileModerationQueueRecord> queue() {
        return submissions.findAll().stream()
                .filter(entity -> entity.isCurrent() || "APPROVED".equals(entity.getModerationStatus()) || "PUBLISHED".equals(entity.getModerationStatus()))
                .map(this::toQueueRecord)
                .sorted((left, right) -> right.submittedAt() == null ? -1 : left.submittedAt() == null ? 1 : right.submittedAt().compareTo(left.submittedAt()))
                .toList();
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord submitForReview(UUID providerAccountId, String publicProfileReference, boolean tenantConsentEnabled) {
        PublicProfileSubmissionEligibilityRecord eligibility = submissionEligibility(providerAccountId, publicProfileReference, tenantConsentEnabled);
        if (!eligibility.submissionEligible()) {
            throw new ProviderOwnershipConflictException(firstBlocker(eligibility.submissionBlockers()), "Public profile is not eligible for review submission.");
        }
        DiscoverPublicProfileSubmissionEntity current = findLatestSubmission(publicProfileReference).orElse(null);
        if (current != null && isBlockingSubmissionStatus(current.getModerationStatus())) {
            return toSubmissionRecord(current);
        }
        PublicProfileDraftWorkspaceRecord draft = draftService.getDraft(providerAccountId, publicProfileReference);
        String submissionReference = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.randomUUID(),
                submissionReference,
                draft.publicProfileReference(),
                draft.publicProfileType(),
                draft.draftReference(),
                draft.currentVersion(),
                "SUBMITTED",
                draft.publicProfileStatus(),
                draft.tenantConsentStatus(),
                toJson(snapshotOwnership(providerAccountId, draft)),
                toJson(snapshotReadiness(draft.readiness())),
                toJson(snapshotContent(draft.sections())),
                toJson(snapshotSourceAttribution(draft.fieldSources())),
                toJson(snapshotMedia(draft.sections())),
                providerAccountId,
                now,
                now,
                now
        );
        try {
            DiscoverPublicProfileSubmissionEntity saved = submissions.save(entity);
            return toSubmissionRecord(saved);
        } catch (DataIntegrityViolationException ex) {
            return findCurrentSubmission(publicProfileReference)
                    .map(this::toSubmissionRecord)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord withdraw(String submissionReference, UUID actorId, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        if (!isWithdrawable(entity.getModerationStatus())) {
            return toSubmissionRecord(entity);
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.withdraw(actorId, now, safeReason(reason), now);
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord startReview(String submissionReference, UUID reviewerId, Long expectedRevision, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if ("UNDER_REVIEW".equals(entity.getModerationStatus())) {
            return toSubmissionRecord(entity);
        }
        if (!"SUBMITTED".equals(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can enter review.");
        }
        entity.startReview(reviewerId, OffsetDateTime.now(), OffsetDateTime.now());
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord requestChanges(String submissionReference, UUID reviewerId, Long expectedRevision, String reason, List<Map<String, Object>> structuredFindings) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if (!List.of("SUBMITTED", "UNDER_REVIEW").contains(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can receive change requests.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.requestChanges(reviewerId, now, safeReason(reason), now);
        submissions.save(entity);
        upsertFindings(entity.getSubmissionReference(), structuredFindings, now);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord approve(String submissionReference, UUID reviewerId, Long expectedRevision, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if ("APPROVED".equals(entity.getModerationStatus())) {
            return toSubmissionRecord(entity);
        }
        if (!List.of("SUBMITTED", "UNDER_REVIEW").contains(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can be approved.");
        }
        entity.approve(reviewerId, OffsetDateTime.now(), safeReason(reason), entity.getSubmittedDraftVersion(), OffsetDateTime.now());
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord reject(String submissionReference, UUID reviewerId, Long expectedRevision, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if ("REJECTED".equals(entity.getModerationStatus())) {
            return toSubmissionRecord(entity);
        }
        if (!List.of("SUBMITTED", "UNDER_REVIEW").contains(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can be rejected.");
        }
        entity.reject(reviewerId, OffsetDateTime.now(), safeReason(reason), OffsetDateTime.now());
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfilePublicationRecord publish(String submissionReference, UUID actorId, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        if (!"APPROVED".equals(entity.getModerationStatus()) && entity.getApprovedVersionNumber() == null) {
            throw new ProviderOwnershipConflictException("public_profile_not_approved", "Only an approved submission can be published.");
        }
        PublicProfilePublicationRecord currentPublication = findCurrentPublication(entity.getPublicProfileReference()).orElse(null);
        if (currentPublication != null && Objects.equals(currentPublication.approvedSubmissionReference(), entity.getSubmissionReference()) && currentPublication.publicationStatus().equals("PUBLISHED")) {
            return currentPublication;
        }
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = buildSnapshot(entity);
        publicProfileService.upsertLifecycleProfile(
                snapshot,
                entity.getSubmittedDraftVersion(),
                "APPROVED",
                "PUBLISHED",
                safeReason(reason),
                OffsetDateTime.now(),
                "PUBLISHED",
                SOURCE_SYSTEM,
                entity.getPublicProfileReference(),
                entity.getSubmittedDraftVersion(),
                entity.getSubmittedAt(),
                0L
        );
        OffsetDateTime now = OffsetDateTime.now();
        DiscoverPublicProfilePublicationEntity publication = publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(entity.getPublicProfileReference())
                .orElseGet(() -> DiscoverPublicProfilePublicationEntity.create(
                        UUID.randomUUID(),
                        UUID.randomUUID().toString(),
                        entity.getPublicProfileReference(),
                        entity.getSubmissionReference(),
                        entity.getSubmittedDraftVersion(),
                        "PUBLISHED",
                        snapshot.canonicalSlug(),
                        snapshot.publicPath(),
                        safeReason(reason),
                        now,
                        now,
                        now
                ));
        publication = publications.save(publication);
        entity.markCurrent(false, now);
        entity.approve(actorId, now, safeReason(reason), entity.getSubmittedDraftVersion(), now);
        submissions.save(entity);
        return toPublicationRecord(publication);
    }

    @Transactional
    public PublicProfilePublicationRecord unpublish(String publicProfileReference, UUID actorId, String reason) {
        DiscoverPublicProfilePublicationEntity entity = publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(publicProfileReference).orElse(null);
        if (entity == null) {
            throw new ProviderOwnershipConflictException("public_profile_not_published", "Public profile is not published.");
        }
        entity.unpublish(safeReason(reason), OffsetDateTime.now(), OffsetDateTime.now());
        publications.save(entity);
        publicProfileService.unpublishPublicProfile(parseUuid(entity.getPublicProfileReference()), SOURCE_SYSTEM, safeReason(reason));
        return toPublicationRecord(entity);
    }

    @Transactional(readOnly = true)
    public List<PublicProfileModerationQueueRecord> listQueue() {
        return queue();
    }

    @Transactional(readOnly = true)
    public List<PublicProfileModerationDecisionRecord> findingsAsDecisions(String submissionReference) {
        return submissions.findBySubmissionReference(submissionReference).map(entity ->
                List.of(new PublicProfileModerationDecisionRecord(
                        entity.getSubmissionReference(),
                        entity.getModerationStatus(),
                        entity.getPublicationStatusSnapshot(),
                        entity.getDecisionAt(),
                        entity.getDecisionById(),
                        entity.getDecisionReason(),
                        entity.getModerationRevision(),
                        entity.isCurrent()
                ))).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfilePublicationRecord> findCurrentPublication(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(publicProfileReference.trim())
                .map(this::toPublicationRecord);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> currentSubmission(String publicProfileReference) {
        return findCurrentSubmission(publicProfileReference).map(this::toSubmissionRecord);
    }

    private DiscoverPublicProfileSubmissionEntity submissionEntity(String submissionReference) {
        if (!StringUtils.hasText(submissionReference)) {
            throw new ProviderOwnershipConflictException("public_profile_draft_not_found", "Submission reference is required.");
        }
        return submissions.findBySubmissionReference(submissionReference.trim())
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Submission not found."));
    }

    private Optional<DiscoverPublicProfileSubmissionEntity> findCurrentSubmission(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return submissions.findFirstByPublicProfileReferenceAndCurrentTrueOrderBySubmittedAtDesc(publicProfileReference.trim());
    }

    private Optional<DiscoverPublicProfileSubmissionEntity> findLatestSubmission(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(publicProfileReference.trim())
                .stream()
                .findFirst();
    }

    private boolean isWithdrawable(String status) {
        return List.of("SUBMITTED", "UNDER_REVIEW").contains(status);
    }

    private boolean isBlockingSubmissionStatus(String status) {
        return List.of("SUBMITTED", "UNDER_REVIEW", "APPROVED", "PUBLISHED").contains(status);
    }

    private void validateRevision(DiscoverPublicProfileSubmissionEntity entity, Long expectedRevision) {
        if (expectedRevision != null && entity.getModerationRevision() != expectedRevision) {
            throw new ProviderOwnershipConflictException("stale_moderation_revision", "Submission has changed since it was loaded.");
        }
    }

    private String firstBlocker(List<String> blockers) {
        if (blockers == null || blockers.isEmpty()) {
            return "public_profile_not_ready";
        }
        return blockers.get(0).toLowerCase(Locale.ROOT);
    }

    private List<String> allowedSubmissionActions(List<String> blockers, PublicProfileDraftWorkspaceRecord draft, DiscoverPublicProfileSubmissionEntity currentSubmission) {
        if (draft == null) {
            return List.of();
        }
        if (!blockers.isEmpty()) {
            return List.of("VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE");
        }
        if (currentSubmission == null) {
            return List.of("EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS", "SUBMIT_FOR_REVIEW");
        }
        return switch (currentSubmission.getModerationStatus()) {
            case "SUBMITTED", "UNDER_REVIEW" -> List.of("VIEW_SUBMITTED_PROFILE", "VIEW_MODERATION_STATUS", "WITHDRAW_SUBMISSION");
            case "CHANGES_REQUESTED" -> List.of("VIEW_REVIEW_FEEDBACK", "EDIT_PUBLIC_PROFILE", "RESUBMIT_FOR_REVIEW");
            case "APPROVED" -> List.of("VIEW_APPROVED_PROFILE", "VIEW_MODERATION_STATUS");
            case "REJECTED" -> List.of("VIEW_REVIEW_FEEDBACK", "START_REVISION");
            case "PUBLISHED" -> List.of("VIEW_PUBLIC_PROFILE", "START_NEW_REVISION");
            default -> List.of("VIEW_PREVIEW", "VIEW_READINESS");
        };
    }

    private Map<String, Object> snapshotOwnership(UUID providerAccountId, PublicProfileDraftWorkspaceRecord draft) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("providerAccountId", providerAccountId == null ? null : providerAccountId.toString());
        value.put("ownershipStatus", draft.ownershipStatus());
        value.put("tenantConsentStatus", draft.tenantConsentStatus());
        value.put("publicProfileStatus", draft.publicProfileStatus());
        value.put("lastSavedAt", draft.lastSavedAt());
        return value;
    }

    private Map<String, Object> snapshotReadiness(PublicProfileDraftReadinessRecord readiness) {
        return readiness == null ? Map.of() : Map.of(
                "readinessStatus", readiness.readinessStatus(),
                "ready", readiness.ready(),
                "completenessPercentage", readiness.completenessPercentage(),
                "missingMandatoryFields", readiness.missingMandatoryFields(),
                "recommendedFields", readiness.recommendedFields(),
                "invalidFields", readiness.invalidFields(),
                "warnings", readiness.warnings(),
                "blockingReasons", readiness.blockingReasons(),
                "lastEvaluatedAt", readiness.lastEvaluatedAt()
        );
    }

    private Map<String, Object> snapshotContent(List<PublicProfileDraftSectionRecord> sections) {
        Map<String, Object> content = new LinkedHashMap<>();
        for (PublicProfileDraftSectionRecord section : sections == null ? List.<PublicProfileDraftSectionRecord>of() : sections) {
            content.put(section.key(), section.content());
        }
        return content;
    }

    private Map<String, Object> snapshotSourceAttribution(Map<String, PublicProfileDraftFieldSourceRecord> sources) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, PublicProfileDraftFieldSourceRecord> entry : (sources == null ? Map.<String, PublicProfileDraftFieldSourceRecord>of() : sources).entrySet()) {
            PublicProfileDraftFieldSourceRecord source = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("sourceSystem", source.sourceSystem());
            value.put("sourceReference", source.sourceReference());
            value.put("sourceRevision", source.sourceRevision());
            value.put("importedAt", source.importedAt());
            value.put("lastEditedBy", source.lastEditedBy());
            value.put("lastEditedAt", source.lastEditedAt());
            value.put("providerOverride", source.providerOverride());
            mapped.put(entry.getKey(), value);
        }
        return mapped;
    }

    private Map<String, Object> snapshotMedia(List<PublicProfileDraftSectionRecord> sections) {
        return sections == null ? Map.of() : sections.stream()
                .filter(section -> "media".equals(section.key()))
                .findFirst()
                .map(PublicProfileDraftSectionRecord::content)
                .orElse(Map.of());
    }

    private void upsertFindings(String submissionReference, List<Map<String, Object>> structuredFindings, OffsetDateTime createdAt) {
        if (!StringUtils.hasText(submissionReference) || structuredFindings == null || structuredFindings.isEmpty()) {
            return;
        }
        for (Map<String, Object> finding : structuredFindings) {
            DiscoverPublicProfileReviewFindingEntity entity = DiscoverPublicProfileReviewFindingEntity.create(
                    UUID.randomUUID(),
                    UUID.randomUUID().toString(),
                    submissionReference,
                    stringValue(finding, "section"),
                    stringValue(finding, "field"),
                    stringValue(finding, "category"),
                    stringValue(finding, "severity"),
                    booleanValue(finding, "required"),
                    stringValue(finding, "reviewerNote"),
                    "OPEN",
                    createdAt
            );
            findings.save(entity);
        }
    }

    private PublicProfileModerationSubmissionRecord toSubmissionRecord(DiscoverPublicProfileSubmissionEntity entity) {
        List<PublicProfileReviewFindingRecord> findingRecords = findings.findBySubmissionReferenceOrderByCreatedAtAsc(entity.getSubmissionReference()).stream()
                .map(this::toFindingRecord)
                .toList();
        return new PublicProfileModerationSubmissionRecord(
                entity.getId(),
                entity.getSubmissionReference(),
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                entity.getDraftReference(),
                entity.getSubmittedDraftVersion(),
                entity.getModerationStatus(),
                entity.getPublicationStatusSnapshot(),
                entity.getTenantConsentStatusSnapshot(),
                mapJson(entity.getOwnershipSnapshotJson()),
                mapJson(entity.getReadinessSnapshotJson()),
                mapJson(entity.getContentSnapshotJson()),
                mapJson(entity.getSourceAttributionSnapshotJson()),
                mapJson(entity.getMediaSnapshotJson()),
                entity.getSubmittedByProviderAccountId(),
                entity.getSubmittedAt(),
                entity.getAssignedReviewerId(),
                entity.getAssignedAt(),
                entity.getDecisionById(),
                entity.getDecisionAt(),
                entity.getDecisionReason(),
                entity.getModerationRevision(),
                entity.isCurrent(),
                entity.getApprovedVersionNumber(),
                entity.getPublishedAt(),
                entity.getUnpublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                findingRecords,
                allowedActions(entity)
        );
    }

    private PublicProfileReviewFindingRecord toFindingRecord(DiscoverPublicProfileReviewFindingEntity entity) {
        return new PublicProfileReviewFindingRecord(
                entity.getId(),
                entity.getFindingReference(),
                entity.getSubmissionReference(),
                entity.getSection(),
                entity.getFieldKey(),
                entity.getCategory(),
                entity.getSeverity(),
                entity.isRequired(),
                entity.getReviewerNote(),
                entity.getResolutionStatus(),
                entity.getProviderResolutionNote(),
                entity.getCreatedAt(),
                entity.getResolvedAt()
        );
    }

    private PublicProfileModerationQueueRecord toQueueRecord(DiscoverPublicProfileSubmissionEntity entity) {
        Map<String, Object> content = mapJson(entity.getContentSnapshotJson());
        Map<String, Object> readiness = mapJson(entity.getReadinessSnapshotJson());
        return new PublicProfileModerationQueueRecord(
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                text(content, "about", "displayName"),
                text(content, "contact", "city"),
                text(content, "contact", "addressLine1"),
                ownershipStatus(entity),
                entity.getTenantConsentStatusSnapshot(),
                text(readiness, "readinessStatus"),
                text(readiness, "readinessStatus"),
                intValue(readiness, "completenessPercentage"),
                entity.getModerationStatus(),
                entity.getPublicationStatusSnapshot(),
                entity.getSubmissionReference(),
                entity.getSubmittedDraftVersion(),
                entity.getSubmittedAt(),
                entity.getAssignedReviewerId() == null ? null : entity.getAssignedReviewerId().toString(),
                entity.getAssignedAt(),
                entity.getSubmittedAt() == null ? 0 : java.time.Duration.between(entity.getSubmittedAt(), OffsetDateTime.now()).toDays(),
                entity.getPublicProfileType() == null ? "PROVIDER_PUBLIC_PROFILE" : entity.getPublicProfileType().name(),
                List.of("VIEW_SUBMITTED_PROFILE", "VIEW_MODERATION_STATUS")
        );
    }

    private PublicProfilePublicationRecord toPublicationRecord(DiscoverPublicProfilePublicationEntity entity) {
        return new PublicProfilePublicationRecord(
                entity.getId(),
                entity.getPublicationReference(),
                entity.getPublicProfileReference(),
                entity.getApprovedSubmissionReference(),
                entity.getPublishedVersion(),
                entity.getPublicationStatus(),
                entity.getSlug(),
                entity.getPublicPath(),
                entity.getReason(),
                entity.getPublishedAt(),
                entity.getUnpublishedAt(),
                entity.isCurrent()
        );
    }

    private PublicProviderProfileModels.PublicProviderProfileSnapshot buildSnapshot(DiscoverPublicProfileSubmissionEntity entity) {
        Map<String, Object> content = mapJson(entity.getContentSnapshotJson());
        Map<String, Object> about = asMap(content.get("about"));
        Map<String, Object> contact = asMap(content.get("contact"));
        Map<String, Object> services = asMap(content.get("services"));
        Map<String, Object> specialities = asMap(content.get("specialities"));
        Map<String, Object> facilities = asMap(content.get("facilities"));
        Map<String, Object> languages = asMap(content.get("languages"));
        Map<String, Object> timings = asMap(content.get("timings"));
        Map<String, Object> media = asMap(content.get("media"));
        Map<String, Object> seo = asMap(content.get("seo"));
        List<PublicProviderProfileModels.PublicProviderLocationSnapshot> locations = List.of(new PublicProviderProfileModels.PublicProviderLocationSnapshot(
                text(contact, "displayName"),
                text(contact, "addressLine1"),
                text(contact, "city"),
                text(contact, "state"),
                text(contact, "country"),
                text(contact, "postalCode"),
                text(timings, "weeklySummary"),
                false,
                false,
                null,
                null
        ));
        List<String> specialityList = list(specialities, "items");
        List<String> serviceList = list(services, "items");
        List<String> languageList = list(languages, "items");
        return new PublicProviderProfileModels.PublicProviderProfileSnapshot(
                parseUuid(entity.getPublicProfileReference()),
                entity.getPublicProfileType(),
                SOURCE_SYSTEM,
                entity.getPublicProfileReference(),
                text(about, "displayName"),
                text(about, "displayName"),
                text(seo, "slug"),
                text(about, "description"),
                text(about, "description"),
                null,
                null,
                resolveEstablishedYear(about),
                null,
                null,
                false,
                languageList,
                specialityList,
                List.of(),
                serviceList,
                List.of(),
                list(facilities, "items"),
                List.of(),
                locations,
                List.of(),
                List.of(),
                parseUuid(text(media, "logoDocumentId")),
                parseUuid(text(media, "coverDocumentId")),
                null,
                text(contact, "publicPhone"),
                text(contact, "publicEmail"),
                text(contact, "website"),
                text(contact, "city"),
                text(contact, "area"),
                text(contact, "state"),
                text(contact, "country"),
                specialityList.isEmpty() ? null : specialityList.getFirst(),
                text(about, "shortTagline"),
                null,
                null,
                null,
                null,
                false,
                1,
                serviceList.size(),
                0,
                0,
                text(contact, "publicPhone") != null ? "CALL_TO_BOOK" : "NOT_AVAILABLE",
                false,
                OffsetDateTime.now(),
                entity.getSubmittedDraftVersion(),
                text(seo, "slug")
        );
    }

    private Map<String, Object> mapJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String text(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer resolveEstablishedYear(Map<String, Object> about) {
        Integer parsed = parseInteger(text(about, "establishedYear"));
        if (parsed != null && parsed >= 1900 && parsed <= 2100) {
            return parsed;
        }
        String registrationNumber = text(about, "registrationNumber");
        if (!StringUtils.hasText(registrationNumber)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(registrationNumber);
        if (!matcher.find()) {
            return null;
        }
        return parseInteger(matcher.group(1));
    }

    private String text(Map<String, Object> map, String outerKey, String innerKey) {
        return text(asMap(map == null ? null : map.get(outerKey)), innerKey);
    }

    private String stringValue(Map<String, Object> map, String key) {
        return text(map, key);
    }

    private boolean booleanValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private List<String> list(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "Updated by moderation workflow";
    }

    private String ownershipStatus(DiscoverPublicProfileSubmissionEntity entity) {
        Map<String, Object> ownership = mapJson(entity.getOwnershipSnapshotJson());
        return text(ownership, "ownershipStatus");
    }

    private List<String> allowedActions(DiscoverPublicProfileSubmissionEntity entity) {
        return switch (entity.getModerationStatus()) {
            case "SUBMITTED", "UNDER_REVIEW" -> List.of("VIEW_SUBMITTED_PROFILE", "VIEW_MODERATION_STATUS", "WITHDRAW_SUBMISSION");
            case "CHANGES_REQUESTED" -> List.of("VIEW_REVIEW_FEEDBACK", "EDIT_PUBLIC_PROFILE", "RESUBMIT_FOR_REVIEW");
            case "APPROVED" -> List.of("VIEW_APPROVED_PROFILE", "VIEW_MODERATION_STATUS");
            case "REJECTED" -> List.of("VIEW_REVIEW_FEEDBACK", "START_REVISION");
            case "PUBLISHED" -> List.of("VIEW_PUBLIC_PROFILE", "START_NEW_REVISION");
            default -> List.of("VIEW_PREVIEW", "VIEW_READINESS");
        };
    }
}
