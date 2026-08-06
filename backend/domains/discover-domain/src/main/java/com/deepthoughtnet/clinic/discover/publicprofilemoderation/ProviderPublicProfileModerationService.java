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
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaContentRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.NotFoundException;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
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
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProviderPublicProfileModerationService(
            ProviderPublicProfileDraftService draftService,
            ProviderOwnershipService ownershipService,
            ProviderPublicProfileService publicProfileService,
            DiscoverPublicProfileSubmissionRepository submissions,
            DiscoverPublicProfileReviewFindingRepository findings,
            DiscoverPublicProfilePublicationRepository publications,
            ObjectStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.draftService = draftService;
        this.ownershipService = ownershipService;
        this.publicProfileService = publicProfileService;
        this.submissions = submissions;
        this.findings = findings;
        this.publications = publications;
        this.storageService = storageService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Transactional(readOnly = true)
    public PublicProfileSubmissionEligibilityRecord submissionEligibility(UUID providerAccountId, String publicProfileReference, boolean tenantConsentEnabled) {
        PublicProfileDraftWorkspaceRecord draft = draftService.getDraft(providerAccountId, publicProfileReference);
        DiscoverPublicProfileSubmissionEntity currentSubmission = findLatestSubmission(publicProfileReference).orElse(null);
        boolean draftDiffersFromSubmission = currentSubmission != null && draftDiffersFromSubmissionSnapshot(draft, currentSubmission);
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
        if (currentSubmission != null && "CHANGES_REQUESTED".equals(currentSubmission.getModerationStatus()) && !draftDiffersFromSubmission) {
            blockers.add("RESUBMISSION_REQUIRED");
        }
        List<String> actions = allowedSubmissionActions(blockers, draft, currentSubmission, draftDiffersFromSubmission);
        return new PublicProfileSubmissionEligibilityRecord(
                blockers.isEmpty(),
                blockers.stream().distinct().toList(),
                actions,
                currentSubmission == null ? "NOT_SUBMITTED" : currentSubmission.getModerationStatus(),
                currentSubmission == null ? "UNPUBLISHED" : publicationStatusFor(currentSubmission),
                currentSubmission == null ? null : currentSubmission.getSubmissionReference(),
                currentSubmission == null ? null : currentSubmission.getSubmittedDraftVersion(),
                currentSubmission == null ? null : currentSubmission.getSubmittedAt(),
                currentSubmission == null ? null : currentSubmission.getDecisionAt(),
                draft == null ? 0 : draft.currentVersion()
        );
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> findSubmission(String publicProfileReference) {
        return findLatestSubmission(publicProfileReference).map(this::toSubmissionRecord);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> findSubmissionByReference(String submissionReference) {
        if (!StringUtils.hasText(submissionReference)) {
            return Optional.empty();
        }
        return submissions.findBySubmissionReference(submissionReference.trim()).map(this::toSubmissionRecord);
    }

    @Transactional(readOnly = true)
    public PublicProfileDraftMediaContentRecord submissionMediaContent(String submissionReference, String mediaReference) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        Map<String, Object> media = mapJson(entity.getMediaSnapshotJson());
        Map<String, Object> metadataByReference = asMap(media.get("mediaMetadataByDocumentId"));
        Object metadata = metadataByReference.get(mediaReference);
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            throw new NotFoundException("Media reference not found.");
        }
        Map<String, Object> normalizedMetadata = asMap(metadataMap);
        String storageKey = text(normalizedMetadata, "storageKey");
        String contentType = text(normalizedMetadata, "contentType");
        String originalFilename = text(normalizedMetadata, "originalFilename");
        if (!StringUtils.hasText(storageKey)) {
            throw new NotFoundException("Media reference not found.");
        }
        byte[] bytes;
        try {
            bytes = storageService.getObjectBytes(storageKey);
        } catch (RuntimeException ex) {
            throw new NotFoundException("Media reference not found.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new NotFoundException("Media reference not found.");
        }
        return new PublicProfileDraftMediaContentRecord(
                mediaReference,
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                StringUtils.hasText(originalFilename) ? originalFilename : mediaReference,
                bytes
        );
    }

    @Transactional(readOnly = true)
    public PublicProfileDraftMediaContentRecord providerSubmissionMediaContent(UUID providerAccountId, String publicProfileReference, String submissionReference, String mediaReference) {
        requireProviderImmutableReadAccess(providerAccountId, publicProfileReference);
        if (!StringUtils.hasText(submissionReference)) {
            throw new NotFoundException("Submission not found.");
        }
        DiscoverPublicProfileSubmissionEntity entity = submissions.findBySubmissionReference(submissionReference.trim())
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        if (!StringUtils.hasText(publicProfileReference) || !publicProfileReference.trim().equals(entity.getPublicProfileReference())) {
            throw new NotFoundException("Submission not found.");
        }
        return submissionMediaContent(submissionReference, mediaReference);
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
                .sorted(Comparator.comparing(DiscoverPublicProfileSubmissionEntity::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DiscoverPublicProfileSubmissionEntity::getModerationRevision, Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        DiscoverPublicProfileSubmissionEntity::getPublicProfileReference,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .filter(entity -> isQueueVisibleStatus(entity.getModerationStatus()))
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
            return findLatestSubmission(publicProfileReference)
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
        return startReview(submissionReference, reviewerId, null, null, null, expectedRevision, reason);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord startReview(String submissionReference, UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, Long expectedRevision, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if ("UNDER_REVIEW".equals(entity.getModerationStatus())) {
            if (hasReviewerGap(entity) && (reviewerId != null || StringUtils.hasText(reviewerReference) || StringUtils.hasText(reviewerDisplayName) || StringUtils.hasText(reviewerEmail))) {
                entity.startReview(reviewerId, safeText(reviewerReference), safeText(reviewerDisplayName), safeText(reviewerEmail), entity.getAssignedAt() == null ? OffsetDateTime.now() : entity.getAssignedAt(), OffsetDateTime.now());
                submissions.save(entity);
            }
            return toSubmissionRecord(entity);
        }
        if (!"SUBMITTED".equals(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can enter review.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.startReview(reviewerId, safeText(reviewerReference), safeText(reviewerDisplayName), safeText(reviewerEmail), now, now);
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord requestChanges(String submissionReference, UUID reviewerId, Long expectedRevision, String reason, List<Map<String, Object>> structuredFindings) {
        return requestChanges(submissionReference, reviewerId, null, null, null, expectedRevision, reason, structuredFindings);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord requestChanges(String submissionReference, UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, Long expectedRevision, String reason, List<Map<String, Object>> structuredFindings) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if (!List.of("SUBMITTED", "UNDER_REVIEW").contains(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a submitted profile can receive change requests.");
        }
        if (!StringUtils.hasText(reason) && (structuredFindings == null || structuredFindings.isEmpty())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Change requests require a reviewer note or structured findings.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.requestChanges(reviewerId, now, safeReason(reason), now);
        submissions.save(entity);
        upsertFindings(entity.getSubmissionReference(), structuredFindings, now);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord approve(String submissionReference, UUID reviewerId, Long expectedRevision, String reason) {
        return approve(submissionReference, reviewerId, null, null, null, expectedRevision, reason);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord approve(String submissionReference, UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, Long expectedRevision, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if ("APPROVED".equals(entity.getModerationStatus())) {
            return toSubmissionRecord(entity);
        }
        if (!"UNDER_REVIEW".equals(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a profile under review can be approved.");
        }
        PublicProfileDraftWorkspaceRecord liveDraft = draftService.findDraft(entity.getPublicProfileReference())
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        if (!"VERIFIED".equalsIgnoreCase(liveDraft.ownershipStatus())) {
            throw new ProviderOwnershipConflictException("ownership_not_verified", "Ownership must remain verified before approval.");
        }
        if (!"ENABLED".equalsIgnoreCase(liveDraft.tenantConsentStatus())) {
            throw new ProviderOwnershipConflictException("tenant_consent_required", "Tenant consent must be enabled before approval.");
        }
        if (liveDraft.readiness() == null || !liveDraft.readiness().ready()) {
            throw new ProviderOwnershipConflictException("public_profile_not_ready", "The public profile must be content-ready before approval.");
        }
        entity.approve(reviewerId, OffsetDateTime.now(), safeReason(reason), entity.getSubmittedDraftVersion(), OffsetDateTime.now());
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord reject(String submissionReference, UUID reviewerId, Long expectedRevision, String reason) {
        return reject(submissionReference, reviewerId, null, null, null, expectedRevision, reason);
    }

    @Transactional
    public PublicProfileModerationSubmissionRecord reject(String submissionReference, UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, Long expectedRevision, String reason) {
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
    public PublicProfileModerationSubmissionRecord addFinding(String submissionReference, UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, Long expectedRevision, Map<String, Object> finding) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        validateRevision(entity, expectedRevision);
        if (!"UNDER_REVIEW".equals(entity.getModerationStatus())) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Only a profile under review can accept findings.");
        }
        if (finding == null || finding.isEmpty()) {
            throw new ProviderOwnershipConflictException("invalid_moderation_transition", "Finding details are required.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        findings.save(DiscoverPublicProfileReviewFindingEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                submissionReference,
                safeText(stringValue(finding, "section")),
                safeText(stringValue(finding, "field")),
                safeText(stringValue(finding, "category")),
                safeText(stringValue(finding, "severity")),
                booleanValue(finding, "required"),
                stringValue(finding, "providerFacingMessage"),
                stringValue(finding, "providerFacingMessage"),
                stringValue(finding, "internalNote"),
                "OPEN",
                now
        ));
        entity.touchReview(now);
        submissions.save(entity);
        return toSubmissionRecord(entity);
    }

    @Transactional
    public PublicProfilePublicationRecord publish(String submissionReference, UUID actorId, String reason) {
        DiscoverPublicProfileSubmissionEntity entity = submissionEntity(submissionReference);
        if (!"APPROVED".equals(entity.getModerationStatus()) || entity.getApprovedVersionNumber() == null) {
            throw new ProviderOwnershipConflictException("public_profile_not_approved", "Only an approved submission can be published.");
        }
        PublicProfileDraftWorkspaceRecord liveDraft = draftService.findDraft(entity.getPublicProfileReference())
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        if (!"VERIFIED".equalsIgnoreCase(liveDraft.ownershipStatus())) {
            throw new ProviderOwnershipConflictException("ownership_not_verified", "Ownership must remain verified before publication.");
        }
        if (!"ENABLED".equalsIgnoreCase(liveDraft.tenantConsentStatus())) {
            throw new ProviderOwnershipConflictException("tenant_consent_required", "Tenant consent must be enabled before publication.");
        }
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = buildSnapshot(entity);
        PublicProviderProfileModels.PublicProviderPublicationRecord projectedProfile = publicProfileService.upsertLifecycleProfile(
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
        DiscoverPublicProfilePublicationEntity currentPublication = publications
                .findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(entity.getPublicProfileReference())
                .orElse(null);
        if (currentPublication != null
                && Objects.equals(currentPublication.getApprovedSubmissionReference(), entity.getSubmissionReference())
                && "PUBLISHED".equals(currentPublication.getPublicationStatus())) {
            return reconcilePublishedLifecycle(entity, currentPublication, actorId, OffsetDateTime.now());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (currentPublication != null) {
            currentPublication.supersede(now);
            publications.saveAndFlush(currentPublication);
        }
        DiscoverPublicProfilePublicationEntity publication = publications.save(DiscoverPublicProfilePublicationEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                entity.getPublicProfileReference(),
                entity.getSubmissionReference(),
                entity.getSubmittedDraftVersion(),
                "PUBLISHED",
                projectedProfile.canonicalSlug(),
                projectedProfile.publicPath(),
                safeReason(reason),
                now,
                actorReference(actorId),
                now,
                now
        ));
        return reconcilePublishedLifecycle(entity, publication, actorId, now);
    }

    @Transactional
    public PublicProfilePublicationRecord reconcilePublishedLifecycle(String submissionReference, UUID actorId) {
        DiscoverPublicProfileSubmissionEntity submission = submissionEntity(submissionReference);
        DiscoverPublicProfilePublicationEntity publication = findCurrentPublicationEntity(submission.getPublicProfileReference())
                .filter(item -> "PUBLISHED".equals(item.getPublicationStatus()))
                .filter(item -> Objects.equals(item.getApprovedSubmissionReference(), submission.getSubmissionReference()))
                .orElseThrow(() -> new ProviderOwnershipConflictException(
                        "published_lifecycle_not_found",
                        "No current published lifecycle record points to the approved submission."
                ));
        return reconcilePublishedLifecycle(submission, publication, actorId, OffsetDateTime.now());
    }

    @Transactional
    public int reconcileCurrentPublishedLifecycles() {
        int reconciled = 0;
        for (DiscoverPublicProfilePublicationEntity publication :
                publications.findByCurrentTrueAndPublicationStatusOrderByPublishedAtAsc("PUBLISHED")) {
            DiscoverPublicProfileSubmissionEntity submission = submissions
                    .findBySubmissionReference(publication.getApprovedSubmissionReference())
                    .orElse(null);
            if (submission == null
                    || !"APPROVED".equals(submission.getModerationStatus())
                    || !Objects.equals(submission.getSubmittedDraftVersion(), publication.getPublishedVersion())
                    || !Objects.equals(submission.getPublicProfileReference(), publication.getPublicProfileReference())) {
                continue;
            }
            publicProfileService.upsertLifecycleProfile(
                    buildSnapshot(submission),
                    submission.getSubmittedDraftVersion(),
                    "APPROVED",
                    "PUBLISHED",
                    safeReason(publication.getReason()),
                    publication.getPublishedAt(),
                    "PUBLISHED",
                    SOURCE_SYSTEM,
                    submission.getPublicProfileReference(),
                    submission.getSubmittedDraftVersion(),
                    submission.getSubmittedAt(),
                    0L
            );
            reconcilePublishedLifecycle(submission, publication, null, OffsetDateTime.now());
            reconciled++;
        }
        return reconciled;
    }

    @Transactional
    public PublicProfilePublicationRecord unpublish(String publicProfileReference, UUID actorId, String reason) {
        DiscoverPublicProfilePublicationEntity entity = publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(publicProfileReference).orElse(null);
        if (entity == null) {
            throw new ProviderOwnershipConflictException("public_profile_not_published", "Public profile is not published.");
        }
        entity.unpublish(safeReason(reason), OffsetDateTime.now(), OffsetDateTime.now());
        publications.save(entity);
        submissions.findBySubmissionReference(entity.getApprovedSubmissionReference()).ifPresent(submission -> {
            OffsetDateTime now = OffsetDateTime.now();
            submission.markUnpublished(now, now);
            submissions.save(submission);
        });
        publicProfileService.unpublishPublicProfile(parseUuid(entity.getPublicProfileReference()), SOURCE_SYSTEM, safeReason(reason));
        return toPublicationRecord(entity, visibilityDecision(publicProfileReference, null));
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
                        publicationStatusFor(entity),
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
        return findCurrentPublicationEntity(publicProfileReference.trim())
                .map(entity -> toPublicationRecord(entity, visibilityDecision(publicProfileReference.trim(), null)));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileModerationSubmissionRecord> currentSubmission(String publicProfileReference) {
        return findLatestSubmission(publicProfileReference).map(this::toSubmissionRecord);
    }

    private DiscoverPublicProfileSubmissionEntity submissionEntity(String submissionReference) {
        if (!StringUtils.hasText(submissionReference)) {
            throw new ProviderOwnershipConflictException("public_profile_draft_not_found", "Submission reference is required.");
        }
        return submissions.findBySubmissionReference(submissionReference.trim())
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Submission not found."));
    }

    private void requireProviderImmutableReadAccess(UUID providerAccountId, String publicProfileReference) {
        if (providerAccountId == null) {
            throw new ForbiddenException("Provider account is required.");
        }
        String normalizedReference = StringUtils.hasText(publicProfileReference) ? publicProfileReference.trim() : null;
        OwnershipRecord ownership = ownershipService.findOwnership(providerAccountId, normalizedReference)
                .orElseThrow(() -> new ForbiddenException("Verified ownership is required before reading submitted media."));
        if (ownership.status() != PublicProfileOwnershipStatus.VERIFIED) {
            throw new ForbiddenException("Verified ownership is required before reading submitted media.");
        }
        boolean activeOwnerMembership = ownershipService.listMemberships(normalizedReference).stream()
                .anyMatch(item -> providerAccountId.equals(item.providerAccountId())
                        && item.role() == com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole.OWNER
                        && "ACTIVE".equalsIgnoreCase(item.status()));
        if (!activeOwnerMembership) {
            throw new ForbiddenException("An active owner membership is required before reading submitted media.");
        }
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

    private List<String> allowedSubmissionActions(List<String> blockers, PublicProfileDraftWorkspaceRecord draft, DiscoverPublicProfileSubmissionEntity currentSubmission, boolean draftDiffersFromSubmission) {
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
            case "CHANGES_REQUESTED" -> draftDiffersFromSubmission
                    ? List.of("VIEW_REVIEW_FEEDBACK", "EDIT_PUBLIC_PROFILE", "SUBMIT_FOR_REVIEW")
                    : List.of("VIEW_REVIEW_FEEDBACK", "EDIT_PUBLIC_PROFILE");
            case "APPROVED" -> List.of("VIEW_APPROVED_PROFILE", "VIEW_MODERATION_STATUS");
            case "REJECTED" -> List.of("VIEW_REVIEW_FEEDBACK", "START_REVISION");
            case "PUBLISHED" -> List.of("VIEW_PUBLIC_PROFILE", "START_NEW_REVISION");
            default -> List.of("VIEW_PREVIEW", "VIEW_READINESS");
        };
    }

    private List<String> moderationAllowedActions(DiscoverPublicProfileSubmissionEntity entity, String publicationStatus) {
        if (entity == null) {
            return List.of();
        }
        if ("PUBLISHED".equals(publicationStatus)) {
            return List.of("VIEW_PUBLIC_PROFILE", "UNPUBLISH_PROFILE", "VIEW_REVIEW_HISTORY");
        }
        return switch (entity.getModerationStatus()) {
            case "SUBMITTED" -> List.of("VIEW_SUBMISSION", "START_REVIEW", "REQUEST_CHANGES", "REJECT_SUBMISSION");
            case "UNDER_REVIEW" -> List.of("VIEW_SUBMISSION", "ADD_REVIEW_FINDING", "REQUEST_CHANGES", "REJECT_SUBMISSION", "APPROVE_SUBMISSION");
            case "CHANGES_REQUESTED" -> List.of("VIEW_SUBMISSION", "VIEW_REVIEW_HISTORY");
            case "APPROVED" -> List.of("VIEW_SUBMISSION", "PUBLISH_PROFILE");
            case "REJECTED", "WITHDRAWN" -> List.of("VIEW_SUBMISSION", "VIEW_REVIEW_HISTORY");
            default -> List.of("VIEW_SUBMISSION");
        };
    }

    private List<String> providerReviewAllowedActions(DiscoverPublicProfileSubmissionEntity entity, String publicationStatus) {
        if (entity == null) {
            return List.of();
        }
        if ("PUBLISHED".equals(publicationStatus)) {
            return List.of("VIEW_PUBLIC_PROFILE", "BACK_TO_WORKSPACE");
        }
        return switch (entity.getModerationStatus()) {
            case "SUBMITTED", "UNDER_REVIEW" -> List.of("BACK_TO_WORKSPACE", "VIEW_SUBMITTED_PROFILE");
            case "CHANGES_REQUESTED" -> List.of("REVIEW_REQUESTED_CHANGES", "OPEN_EDITABLE_DRAFT", "BACK_TO_WORKSPACE");
            case "APPROVED" -> List.of("VIEW_APPROVAL_STATUS", "BACK_TO_WORKSPACE");
            case "REJECTED" -> List.of("VIEW_REJECTION_STATUS", "START_NEW_REVISION", "BACK_TO_WORKSPACE");
            default -> List.of("BACK_TO_WORKSPACE");
        };
    }

    private VisibilityDecision visibilityDecision(String publicProfileReference, DiscoverPublicProfileSubmissionEntity entity) {
        String fallbackUrl = draftService.findDraft(publicProfileReference).map(PublicProfileDraftWorkspaceRecord::publicProfilePath).orElse(null);
        DiscoverPublicProfilePublicationEntity publication = findCurrentPublicationEntity(publicProfileReference).orElse(null);
        if (publication == null || !"PUBLISHED".equals(publication.getPublicationStatus())) {
            return new VisibilityDecision("NOT_PUBLISHED", "Profile is not published.", fallbackUrl, null, null);
        }
        PublicProfileDraftWorkspaceRecord liveDraft = draftService.findDraft(publicProfileReference).orElse(null);
        String ownershipStatus = liveDraft == null ? (entity == null ? null : ownershipStatus(entity)) : liveDraft.ownershipStatus();
        String tenantConsentStatus = liveDraft == null ? (entity == null ? null : entity.getTenantConsentStatusSnapshot()) : liveDraft.tenantConsentStatus();
        if (!"VERIFIED".equalsIgnoreCase(ownershipStatus)) {
            return new VisibilityDecision("HIDDEN_BY_OWNERSHIP", "Ownership is not verified.", publication.getPublicPath(), publication.getPublishedVersion(), publication.getPublishedAt());
        }
        if (!"ENABLED".equalsIgnoreCase(tenantConsentStatus)) {
            return new VisibilityDecision("HIDDEN_BY_TENANT_CONSENT", "Tenant consent is disabled.", publication.getPublicPath(), publication.getPublishedVersion(), publication.getPublishedAt());
        }
        return new VisibilityDecision("VISIBLE", "Published profile is publicly visible.", publication.getPublicPath(), publication.getPublishedVersion(), publication.getPublishedAt());
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
                "lastEvaluatedAt", readiness.lastEvaluatedAt(),
                "evaluatedDraftVersion", readiness.evaluatedDraftVersion()
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

    private boolean draftDiffersFromSubmissionSnapshot(PublicProfileDraftWorkspaceRecord draft, DiscoverPublicProfileSubmissionEntity submission) {
        if (draft == null || submission == null) {
            return false;
        }
        return !Objects.equals(snapshotContent(draft.sections()), mapJson(submission.getContentSnapshotJson()));
    }

    private void upsertFindings(String submissionReference, List<Map<String, Object>> structuredFindings, OffsetDateTime createdAt) {
        if (!StringUtils.hasText(submissionReference) || structuredFindings == null || structuredFindings.isEmpty()) {
            return;
        }
        List<Map<String, Object>> normalizedIncoming = normalizeFindingPayload(structuredFindings);
        List<Map<String, Object>> normalizedExisting = findings.findBySubmissionReferenceOrderByCreatedAtAsc(submissionReference).stream()
                .map(this::normalizeFindingRecord)
                .sorted(Comparator.comparing((Map<String, Object> value) -> String.valueOf(value.get("section")))
                        .thenComparing(value -> String.valueOf(value.get("field")))
                        .thenComparing(value -> String.valueOf(value.get("category")))
                        .thenComparing(value -> String.valueOf(value.get("severity")))
                        .thenComparing(value -> String.valueOf(value.get("providerFacingMessage")))
                        .thenComparing(value -> String.valueOf(value.get("internalNote"))))
                .toList();
        if (normalizedExisting.equals(normalizedIncoming)) {
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
                    stringValue(finding, "providerFacingMessage"),
                    stringValue(finding, "providerFacingMessage"),
                    stringValue(finding, "internalNote"),
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
        VisibilityDecision visibility = visibilityDecision(entity.getPublicProfileReference(), entity);
        String publicationStatus = publicationStatusFor(entity);
        return new PublicProfileModerationSubmissionRecord(
                entity.getId(),
                entity.getSubmissionReference(),
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                entity.getDraftReference(),
                entity.getSubmittedDraftVersion(),
                entity.getModerationStatus(),
                publicationStatus,
                entity.getTenantConsentStatusSnapshot(),
                mapJson(entity.getOwnershipSnapshotJson()),
                mapJson(entity.getReadinessSnapshotJson()),
                mapJson(entity.getContentSnapshotJson()),
                mapJson(entity.getSourceAttributionSnapshotJson()),
                mapJson(entity.getMediaSnapshotJson()),
                entity.getSubmittedByProviderAccountId(),
                entity.getSubmittedAt(),
                entity.getAssignedReviewerId(),
                entity.getAssignedReviewerReference(),
                entity.getAssignedReviewerDisplayName(),
                entity.getAssignedReviewerEmail(),
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
                visibility.effectiveVisibility(),
                visibility.visibilityReason(),
                visibility.publicUrl(),
                findingRecords,
                providerReviewAllowedActions(entity, publicationStatus),
                moderationAllowedActions(entity, publicationStatus)
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
                entity.getProviderFacingMessage(),
                entity.getInternalNote(),
                entity.getResolutionStatus(),
                entity.getProviderResolutionNote(),
                entity.getCreatedAt(),
                entity.getResolvedAt()
        );
    }

    private PublicProfileModerationQueueRecord toQueueRecord(DiscoverPublicProfileSubmissionEntity entity) {
        Map<String, Object> content = mapJson(entity.getContentSnapshotJson());
        Map<String, Object> readiness = mapJson(entity.getReadinessSnapshotJson());
        VisibilityDecision visibility = visibilityDecision(entity.getPublicProfileReference(), entity);
        String publicationStatus = publicationStatusFor(entity);
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
                publicationStatus,
                entity.getSubmissionReference(),
                entity.getSubmittedDraftVersion(),
                entity.getSubmittedAt(),
                reviewerLabel(entity),
                entity.getAssignedAt(),
                entity.getSubmittedAt() == null ? 0 : java.time.Duration.between(entity.getSubmittedAt(), OffsetDateTime.now()).toDays(),
                entity.getPublicProfileType() == null ? "PROVIDER_PUBLIC_PROFILE" : entity.getPublicProfileType().name(),
                visibility.effectiveVisibility(),
                visibility.visibilityReason(),
                visibility.publicUrl(),
                moderationAllowedActions(entity, publicationStatus)
        );
    }

    private PublicProfilePublicationRecord toPublicationRecord(DiscoverPublicProfilePublicationEntity entity, VisibilityDecision visibility) {
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
                entity.isCurrent(),
                visibility == null ? null : visibility.effectiveVisibility(),
                visibility == null ? null : visibility.visibilityReason()
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
                publicPath(entity.getPublicProfileType(), text(seo, "slug"))
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

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean hasReviewerGap(DiscoverPublicProfileSubmissionEntity entity) {
        return entity.getAssignedReviewerId() == null
                && !StringUtils.hasText(entity.getAssignedReviewerReference())
                && !StringUtils.hasText(entity.getAssignedReviewerDisplayName())
                && !StringUtils.hasText(entity.getAssignedReviewerEmail());
    }

    private String reviewerLabel(DiscoverPublicProfileSubmissionEntity entity) {
        if (StringUtils.hasText(entity.getAssignedReviewerDisplayName())) {
            return entity.getAssignedReviewerDisplayName();
        }
        if (StringUtils.hasText(entity.getAssignedReviewerReference())) {
            return entity.getAssignedReviewerReference();
        }
        if (entity.getAssignedReviewerId() != null) {
            return entity.getAssignedReviewerId().toString();
        }
        return "Unassigned";
    }

    private String publicPath(ProviderType providerType, String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        String cleanSlug = slug.trim();
        return switch (providerType == null ? ProviderType.CLINIC : providerType) {
            case INDIVIDUAL_DOCTOR -> "/discover/doctors/" + cleanSlug;
            case HOSPITAL -> "/discover/hospitals/" + cleanSlug;
            case CLINIC -> "/discover/clinics/" + cleanSlug;
        };
    }

    private String ownershipStatus(DiscoverPublicProfileSubmissionEntity entity) {
        Map<String, Object> ownership = mapJson(entity.getOwnershipSnapshotJson());
        return text(ownership, "ownershipStatus");
    }

    private boolean isQueueVisibleStatus(String status) {
        return List.of("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "PUBLISHED").contains(status);
    }

    private Optional<DiscoverPublicProfilePublicationEntity> findCurrentPublicationEntity(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(publicProfileReference.trim());
    }

    private String publicationStatusFor(DiscoverPublicProfileSubmissionEntity submission) {
        if (submission == null) {
            return "UNPUBLISHED";
        }
        return findCurrentPublicationEntity(submission.getPublicProfileReference())
                .filter(publication -> Objects.equals(
                        publication.getApprovedSubmissionReference(),
                        submission.getSubmissionReference()
                ))
                .map(DiscoverPublicProfilePublicationEntity::getPublicationStatus)
                .orElse(submission.getPublicationStatusSnapshot());
    }

    private PublicProfilePublicationRecord reconcilePublishedLifecycle(
            DiscoverPublicProfileSubmissionEntity submission,
            DiscoverPublicProfilePublicationEntity publication,
            UUID actorId,
            OffsetDateTime reconciledAt
    ) {
        boolean publicationAuditMissing = !StringUtils.hasText(publication.getPublishedBy());
        publication.setPublishedByIfMissing(actorReference(actorId), reconciledAt);
        if (publicationAuditMissing) {
            publications.save(publication);
        }
        if (!"PUBLISHED".equals(submission.getPublicationStatusSnapshot())
                || !Objects.equals(submission.getPublishedAt(), publication.getPublishedAt())
                || submission.getUnpublishedAt() != null) {
            submission.markPublished(publication.getPublishedAt(), reconciledAt);
            submissions.save(submission);
        }
        return toPublicationRecord(publication, visibilityDecision(submission.getPublicProfileReference(), submission));
    }

    private String actorReference(UUID actorId) {
        return actorId == null ? "system:lifecycle-reconciliation" : actorId.toString();
    }

    private List<Map<String, Object>> normalizeFindingPayload(List<Map<String, Object>> structuredFindings) {
        Comparator<Map<String, Object>> comparator = Comparator.comparing((Map<String, Object> value) -> String.valueOf(value.get("section")))
                .thenComparing(value -> String.valueOf(value.get("field")))
                .thenComparing(value -> String.valueOf(value.get("category")))
                .thenComparing(value -> String.valueOf(value.get("severity")))
                .thenComparing(value -> String.valueOf(value.get("reviewerNote")));
        return structuredFindings.stream()
                .map(this::normalizeFinding)
                .sorted(comparator)
                .toList();
    }

    private Map<String, Object> normalizeFindingRecord(DiscoverPublicProfileReviewFindingEntity entity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("section", entity.getSection());
        value.put("field", entity.getFieldKey());
        value.put("category", entity.getCategory());
        value.put("severity", entity.getSeverity());
        value.put("required", entity.isRequired());
        value.put("reviewerNote", entity.getReviewerNote());
        value.put("providerFacingMessage", entity.getProviderFacingMessage());
        value.put("internalNote", entity.getInternalNote());
        return value;
    }

    private Map<String, Object> normalizeFinding(Map<String, Object> finding) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("section", stringValue(finding, "section"));
        value.put("field", stringValue(finding, "field"));
        value.put("category", stringValue(finding, "category"));
        value.put("severity", stringValue(finding, "severity"));
        value.put("required", booleanValue(finding, "required"));
        String providerFacingMessage = stringValue(finding, "providerFacingMessage");
        value.put("reviewerNote", StringUtils.hasText(providerFacingMessage) ? providerFacingMessage : stringValue(finding, "reviewerNote"));
        value.put("providerFacingMessage", providerFacingMessage);
        value.put("internalNote", stringValue(finding, "internalNote"));
        return value;
    }

    private record VisibilityDecision(String effectiveVisibility, String visibilityReason, String publicUrl, Integer publishedVersion, OffsetDateTime publishedAt) {
    }
}
