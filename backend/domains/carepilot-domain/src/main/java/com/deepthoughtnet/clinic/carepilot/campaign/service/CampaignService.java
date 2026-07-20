package com.deepthoughtnet.clinic.carepilot.campaign.service;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.carepilot.shared.exception.CampaignConflictException;
import com.deepthoughtnet.clinic.platform.core.errors.BadRequestException;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final CampaignRepository repository;
    private final CampaignTemplateRepository templateRepository;
    private final CampaignApprovalHistoryRepository historyRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;

    public CampaignService(
            CampaignRepository repository,
            CampaignTemplateRepository templateRepository,
            CampaignApprovalHistoryRepository historyRepository,
            AppUserRepository appUserRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.historyRepository = historyRepository;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CampaignRecord create(UUID tenantId, CampaignCreateCommand command, UUID actorId, String actorRole) {
        CarePilotValidators.requireTenant(tenantId);
        if (!isEngageManager(actorRole)) {
            throw new ForbiddenException("Only Engage Manager can create campaigns");
        }
        CarePilotValidators.requireText(command.name(), "name");
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        String campaignReference = allocateCampaignReference(tenantId);
        CampaignEntity entity = CampaignEntity.create(
                tenantId,
                campaignReference,
                command.name().trim(),
                command.campaignType() == null ? CampaignType.CUSTOM : command.campaignType(),
                command.triggerType() == null ? TriggerType.MANUAL : command.triggerType(),
                command.audienceType() == null ? AudienceType.ALL_PATIENTS : command.audienceType(),
                command.templateId(),
                normalizeNullable(command.notes()),
                actorId
        );
        CampaignRecord saved = toRecord(repository.save(entity));
        appendHistory(
                tenantId,
                saved.id(),
                "CREATED",
                null,
                saved.status(),
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                null,
                null,
                saved.version(),
                saved.version(),
                null,
                saved.approvedConfigurationHash(),
                saved.approvedConfigurationHash()
        );
        return saved;
    }

    @Transactional
    public CampaignRecord update(UUID tenantId, UUID campaignId, CampaignCreateCommand command, UUID actorId, String actorRole) {
        if (!isEngageManager(actorRole)) {
            throw new ForbiddenException("Only Engage Manager can edit campaign content");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        CampaignStatus before = entity.getStatus();
        Integer previousVersion = entity.getVersion();
        String previousHash = configurationHash(entity);
        if (before == CampaignStatus.PENDING_APPROVAL) {
            throw new CampaignConflictException("Withdraw the pending submission before editing this campaign");
        }
        if (before == CampaignStatus.APPROVED || before == CampaignStatus.ACTIVE || before == CampaignStatus.PAUSED) {
            throw new CampaignConflictException("Approved or active campaigns are locked. Duplicate the campaign to create a new version.");
        }
        if (before == CampaignStatus.CANCELLED || before == CampaignStatus.COMPLETED) {
            throw new CampaignConflictException("Completed or cancelled campaigns cannot be edited");
        }
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);

        String name = command.name() == null ? entity.getName() : command.name().trim();
        CampaignType type = command.campaignType() == null ? entity.getCampaignType() : command.campaignType();
        TriggerType triggerType = command.triggerType() == null ? entity.getTriggerType() : command.triggerType();
        AudienceType audienceType = command.audienceType() == null ? entity.getAudienceType() : command.audienceType();
        UUID templateId = command.templateId() == null ? entity.getTemplateId() : command.templateId();
        String notes = command.notes() == null ? entity.getNotes() : normalizeNullable(command.notes());
        entity.updateDraft(name, type, triggerType, audienceType, templateId, notes);
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "UPDATED",
                before,
                saved.getStatus(),
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                null,
                previousVersion,
                saved.getVersion(),
                saved.getVersion(),
                previousHash,
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord editAndResubmit(
            UUID tenantId,
            UUID campaignId,
            CampaignCreateCommand command,
            UUID actorId,
            String actorRole,
            Integer expectedVersion,
            String resolutionNote
    ) {
        if (!isEngageManager(actorRole)) {
            throw new ForbiddenException("Only Engage Manager can edit and resubmit campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.CHANGES_REQUESTED) {
            throw new CampaignConflictException("Only changes requested campaigns can be edited and resubmitted");
        }
        ensureExpectedVersion(entity, expectedVersion);
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);

        Integer previousVersion = entity.getVersion();
        String previousHash = configurationHash(entity);
        applyEditableChanges(entity, command);
        validateSubmitReadiness(entity);

        String newHash = configurationHash(entity);
        String normalizedResolutionNote = normalizeNullable(resolutionNote);
        if (previousHash.equals(newHash) && (normalizedResolutionNote == null || normalizedResolutionNote.isBlank())) {
            throw new CampaignConflictException("Save a campaign change or provide a resolution note before resubmitting.");
        }

        entity.submit(actorId, snapshot, previousVersion, newHash);
        CampaignEntity saved = repository.saveAndFlush(entity);
        appendHistory(
                tenantId,
                campaignId,
                "RESUBMITTED_FOR_APPROVAL",
                CampaignStatus.CHANGES_REQUESTED,
                CampaignStatus.PENDING_APPROVAL,
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                normalizedResolutionNote,
                previousVersion,
                saved.getVersion(),
                saved.getVersion(),
                previousHash,
                newHash,
                newHash
        );
        return toRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignRecord> list(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignRecord> listPendingApproval(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toRecord)
                .filter(record -> record.status() == CampaignStatus.PENDING_APPROVAL)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignRecord> lookup(UUID tenantId, String query, int limit) {
        CarePilotValidators.requireTenant(tenantId);
        String term = normalizeNullable(query);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toRecord)
                .filter(record -> term == null || term.isBlank() || matchesLookup(record, term))
                .limit(safeLimit)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CampaignRecord> find(UUID tenantId, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        return repository.findByTenantIdAndId(tenantId, campaignId).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<CampaignApprovalHistoryEntity> history(UUID tenantId, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        return historyRepository.findByTenantIdAndCampaignIdOrderByCreatedAtAsc(tenantId, campaignId);
    }

    @Transactional
    public CampaignRecord submitForApproval(UUID tenantId, UUID campaignId, UUID actorId, String actorRole, Integer expectedVersion) {
        return submitForApproval(tenantId, campaignId, actorId, actorRole, expectedVersion, null);
    }

    @Transactional
    public CampaignRecord submitForApproval(UUID tenantId, UUID campaignId, UUID actorId, String actorRole, Integer expectedVersion, String resolutionNote) {
        if (!isEngageManager(actorRole)) {
            throw new ForbiddenException("Only Engage Manager can submit campaigns for approval");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        CampaignStatus from = entity.getStatus();
        if (entity.getStatus() != CampaignStatus.DRAFT && entity.getStatus() != CampaignStatus.CHANGES_REQUESTED) {
            throw new CampaignConflictException("Only draft or changes requested campaigns can be submitted for approval");
        }
        validateSubmitReadiness(entity);
        ensureExpectedVersion(entity, expectedVersion);
        Integer reviewedVersion = entity.getApprovedVersion();
        String reviewedHash = entity.getApprovedConfigurationHash();
        String hash = configurationHash(entity);
        String normalizedResolutionNote = normalizeNullable(resolutionNote);
        if (from == CampaignStatus.CHANGES_REQUESTED) {
            if (reviewedVersion == null || reviewedHash == null) {
                throw new CampaignConflictException("Save a campaign change or provide a resolution note before resubmitting.");
            }
            if (entity.getVersion() <= reviewedVersion) {
                throw new CampaignConflictException("Save a campaign change or provide a resolution note before resubmitting.");
            }
            if (reviewedHash.equals(hash) && (normalizedResolutionNote == null || normalizedResolutionNote.isBlank())) {
                throw new CampaignConflictException("Save a campaign change or provide a resolution note before resubmitting.");
            }
        }
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        entity.submit(actorId, snapshot, entity.getVersion(), hash);
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                from == CampaignStatus.CHANGES_REQUESTED ? "RESUBMITTED_FOR_APPROVAL" : "SUBMITTED",
                from,
                CampaignStatus.PENDING_APPROVAL,
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                normalizedResolutionNote,
                reviewedVersion,
                saved.getVersion(),
                saved.getVersion(),
                reviewedHash,
                hash,
                hash
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord withdrawSubmission(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isEngageManager(actorRole)) {
            throw new ForbiddenException("Only Engage Manager can withdraw a submission");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.PENDING_APPROVAL) {
            throw new CampaignConflictException("Only pending approval campaigns can be withdrawn");
        }
        if (entity.getSubmittedBy() != null && !entity.getSubmittedBy().equals(actorId)) {
            throw new ForbiddenException("Only the submitter can withdraw a submission");
        }
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        CampaignStatus from = entity.getStatus();
        entity.withdraw(actorId);
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "WITHDRAWN",
                from,
                saved.getStatus(),
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                null,
                entity.getApprovedVersion(),
                saved.getVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord approve(UUID tenantId, UUID campaignId, UUID actorId, String actorRole, String comment, Integer expectedVersion) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can approve campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.PENDING_APPROVAL) {
            throw new CampaignConflictException("Only pending approval campaigns can be approved");
        }
        if (entity.getSubmittedBy() != null && entity.getSubmittedBy().equals(actorId)) {
            throw new ForbiddenException("A reviewer cannot approve a campaign they submitted");
        }
        ensureExpectedVersion(entity, expectedVersion);
        validateSubmitReadiness(entity);
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        Integer previousVersion = entity.getApprovedVersion();
        String previousHash = entity.getApprovedConfigurationHash();
        String hash = configurationHash(entity);
        entity.approve(actorId, snapshot, normalizeNullable(comment), entity.getVersion(), hash);
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "APPROVED",
                CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.APPROVED,
                actorId,
                actorRole,
                snapshot,
                normalizeNullable(comment),
                null,
                null,
                previousVersion,
                saved.getVersion(),
                saved.getVersion(),
                previousHash,
                hash,
                hash
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord requestChanges(UUID tenantId, UUID campaignId, UUID actorId, String actorRole, String comment, Integer expectedVersion) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can request changes");
        }
        if (comment == null || comment.isBlank()) {
            throw new BadRequestException("Review comment is required");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.PENDING_APPROVAL) {
            throw new CampaignConflictException("Only pending approval campaigns can be sent back for changes");
        }
        if (entity.getSubmittedBy() != null && entity.getSubmittedBy().equals(actorId)) {
            throw new ForbiddenException("A reviewer cannot request changes on a campaign they submitted");
        }
        ensureExpectedVersion(entity, expectedVersion);
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        entity.requestChanges(actorId, snapshot, comment.trim());
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "CHANGES_REQUESTED",
                CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.CHANGES_REQUESTED,
                actorId,
                actorRole,
                snapshot,
                comment.trim(),
                null,
                null,
                entity.getApprovedVersion(),
                saved.getVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord activate(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can activate campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.APPROVED) {
            throw new CampaignConflictException("Only approved campaigns can be activated");
        }
        verifyApprovedSnapshot(entity);
        validateSubmitReadiness(entity);
        CampaignEntity.ActorSnapshot snapshot = resolveActorSnapshot(tenantId, actorId, actorRole);
        entity.activate(actorId, snapshot);
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "ACTIVATED",
                CampaignStatus.APPROVED,
                CampaignStatus.ACTIVE,
                actorId,
                actorRole,
                snapshot,
                null,
                null,
                null,
                entity.getApprovedVersion(),
                saved.getVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord activate(UUID tenantId, UUID campaignId) {
        return activate(tenantId, campaignId, null, null);
    }

    @Transactional
    public CampaignRecord pause(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can pause campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.ACTIVE) {
            throw new CampaignConflictException("Only active campaigns can be paused");
        }
        entity.pause();
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "PAUSED",
                CampaignStatus.ACTIVE,
                CampaignStatus.PAUSED,
                actorId,
                actorRole,
                resolveActorSnapshot(tenantId, actorId, actorRole),
                null,
                null,
                null,
                entity.getApprovedVersion(),
                saved.getVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord resume(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can resume campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.PAUSED) {
            throw new CampaignConflictException("Only paused campaigns can be resumed");
        }
        verifyApprovedSnapshot(entity);
        entity.resume();
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "RESUMED",
                CampaignStatus.PAUSED,
                CampaignStatus.ACTIVE,
                actorId,
                actorRole,
                resolveActorSnapshot(tenantId, actorId, actorRole),
                null,
                null,
                null,
                entity.getApprovedVersion(),
                saved.getVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord deactivate(UUID tenantId, UUID campaignId) {
        return pause(tenantId, campaignId, null, null);
    }

    @Transactional
    public CampaignRecord deactivate(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        return pause(tenantId, campaignId, actorId, actorRole);
    }

    @Transactional
    public CampaignRecord cancel(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can cancel campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        CampaignStatus before = entity.getStatus();
        if (before == CampaignStatus.CANCELLED || before == CampaignStatus.COMPLETED) {
            throw new CampaignConflictException("Campaign is already finished");
        }
        entity.cancel();
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "CANCELLED",
                before,
                CampaignStatus.CANCELLED,
                actorId,
                actorRole,
                resolveActorSnapshot(tenantId, actorId, actorRole),
                null,
                null,
                null,
                entity.getApprovedVersion(),
                entity.getApprovedVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    @Transactional
    public CampaignRecord complete(UUID tenantId, UUID campaignId, UUID actorId, String actorRole) {
        if (!isClinicAdmin(actorRole)) {
            throw new ForbiddenException("Only Clinic Admin can complete campaigns");
        }
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        if (entity.getStatus() != CampaignStatus.ACTIVE && entity.getStatus() != CampaignStatus.PAUSED) {
            throw new CampaignConflictException("Only active or paused campaigns can be completed");
        }
        entity.complete();
        CampaignEntity saved = repository.save(entity);
        appendHistory(
                tenantId,
                campaignId,
                "COMPLETED",
                CampaignStatus.ACTIVE,
                CampaignStatus.COMPLETED,
                actorId,
                actorRole,
                resolveActorSnapshot(tenantId, actorId, actorRole),
                null,
                null,
                null,
                entity.getApprovedVersion(),
                entity.getApprovedVersion(),
                saved.getVersion(),
                entity.getApprovedConfigurationHash(),
                configurationHash(saved),
                configurationHash(saved)
        );
        return toRecord(saved);
    }

    private CampaignEntity requireCampaign(UUID tenantId, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        return repository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
    }

    private void validateSubmitReadiness(CampaignEntity entity) {
        List<String> errors = new java.util.ArrayList<>();
        if (entity.getName() == null || entity.getName().isBlank()) {
            errors.add("Campaign name is required");
        }
        if (entity.getCampaignType() == null) {
            errors.add("Campaign type is required");
        }
        if (entity.getTriggerType() == null) {
            errors.add("Trigger type is required");
        }
        if (entity.getAudienceType() == null) {
            errors.add("Audience definition is required");
        }
        if (entity.getTemplateId() == null) {
            errors.add("An active template is required before submission");
        } else {
            CampaignTemplateEntity template = templateRepository.findByTenantIdAndId(entity.getTenantId(), entity.getTemplateId())
                    .orElse(null);
            if (template == null) {
                errors.add("Selected template was not found");
            } else if (!template.isActive()) {
                errors.add("Selected template must be active");
            }
            if (template != null && template.getChannelType() == null) {
                errors.add("Template channel is required");
            }
        }
        if (entity.getTriggerType() == TriggerType.SCHEDULED && entity.getTemplateId() == null) {
            errors.add("Scheduled campaigns require a schedule");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join(". ", errors));
        }
    }

    private void ensureExpectedVersion(CampaignEntity entity, Integer expectedVersion) {
        if (expectedVersion != null && expectedVersion.intValue() != entity.getVersion()) {
            throw new CampaignConflictException("Campaign was updated by another user. Refresh and try again.");
        }
    }

    private void verifyApprovedSnapshot(CampaignEntity entity) {
        if (entity.getApprovedConfigurationHash() == null) {
            throw new CampaignConflictException("Campaign approval snapshot is missing. Reapprove before changing the runtime state.");
        }
        String currentHash = configurationHash(entity);
        if (!entity.getApprovedConfigurationHash().equals(currentHash)) {
            throw new CampaignConflictException("Campaign configuration changed after approval. Reapprove before changing the runtime state.");
        }
    }

    private boolean matchesLookup(CampaignRecord record, String term) {
        String normalized = term.toLowerCase(Locale.ROOT);
        return (record.name() != null && record.name().toLowerCase(Locale.ROOT).contains(normalized))
                || record.campaignType() != null && record.campaignType().name().toLowerCase(Locale.ROOT).contains(normalized)
                || (record.campaignReference() != null && record.campaignReference().toLowerCase(Locale.ROOT).contains(normalized))
                || record.id().toString().contains(normalized);
    }

    private boolean isClinicAdmin(String actorRole) {
        return actorRole != null && actorRole.equalsIgnoreCase("CLINIC_ADMIN");
    }

    private boolean isEngageManager(String actorRole) {
        return actorRole != null && actorRole.equalsIgnoreCase("ENGAGE_MANAGER");
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void applyEditableChanges(CampaignEntity entity, CampaignCreateCommand command) {
        String name = command.name() == null ? entity.getName() : command.name().trim();
        CampaignType type = command.campaignType() == null ? entity.getCampaignType() : command.campaignType();
        TriggerType triggerType = command.triggerType() == null ? entity.getTriggerType() : command.triggerType();
        AudienceType audienceType = command.audienceType() == null ? entity.getAudienceType() : command.audienceType();
        UUID templateId = command.templateId() == null ? entity.getTemplateId() : command.templateId();
        String notes = command.notes() == null ? entity.getNotes() : normalizeNullable(command.notes());
        entity.updateDraft(name, type, triggerType, audienceType, templateId, notes);
    }

    private CampaignRecord toRecord(CampaignEntity entity) {
        return new CampaignRecord(
                entity.getId(),
                entity.getCampaignReference(),
                entity.getTenantId(),
                entity.getName(),
                entity.getCampaignType(),
                entity.getStatus(),
                entity.getTriggerType(),
                entity.getAudienceType(),
                entity.getTemplateId(),
                entity.isActive(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getSubmittedBy(),
                entity.getSubmittedByDisplayName(),
                entity.getSubmittedByRoleLabel(),
                entity.getSubmittedByEmployeeCode(),
                entity.getSubmittedByUsername(),
                entity.getSubmittedAt(),
                entity.getReviewedBy(),
                entity.getReviewedByDisplayName(),
                entity.getReviewedByRoleLabel(),
                entity.getReviewedByEmployeeCode(),
                entity.getReviewedByUsername(),
                entity.getReviewedAt(),
                entity.getReviewComment(),
                entity.getApprovedBy(),
                entity.getApprovedByDisplayName(),
                entity.getApprovedByRoleLabel(),
                entity.getApprovedByEmployeeCode(),
                entity.getApprovedByUsername(),
                entity.getApprovedAt(),
                entity.getActivationBy(),
                entity.getActivationByDisplayName(),
                entity.getActivationByRoleLabel(),
                entity.getActivationByEmployeeCode(),
                entity.getActivationByUsername(),
                entity.getActivationAt(),
                entity.getApprovalInvalidatedReason(),
                entity.getApprovedVersion(),
                entity.getApprovedConfigurationHash(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void appendHistory(
            UUID tenantId,
            UUID campaignId,
            String eventType,
            CampaignStatus from,
            CampaignStatus to,
            UUID actorId,
            String actorRole,
            CampaignEntity.ActorSnapshot actorSnapshot,
            String comment,
            String invalidationReason,
            String resolutionNote,
            Integer previousCampaignVersion,
            Integer campaignVersion,
            Integer newCampaignVersion,
            String previousConfigurationHash,
            String configurationHash,
            String newConfigurationHash
    ) {
        historyRepository.save(CampaignApprovalHistoryEntity.create(
                tenantId,
                campaignId,
                eventType,
                from,
                to,
                actorId,
                actorRole,
                actorSnapshot == null ? null : actorSnapshot.displayName(),
                actorSnapshot == null ? null : actorSnapshot.roleLabel(),
                actorSnapshot == null ? null : actorSnapshot.employeeCode(),
                actorSnapshot == null ? null : actorSnapshot.username(),
                comment,
                invalidationReason,
                resolutionNote,
                previousCampaignVersion,
                campaignVersion,
                newCampaignVersion,
                previousConfigurationHash,
                configurationHash,
                newConfigurationHash
        ));
    }

    private String configurationHash(CampaignEntity entity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            CampaignTemplateEntity template = entity.getTemplateId() == null ? null : templateRepository.findByTenantIdAndId(entity.getTenantId(), entity.getTemplateId()).orElse(null);
            String payload = String.join("|",
                    safe(entity.getName()),
                    safe(entity.getCampaignType() == null ? null : entity.getCampaignType().name()),
                    safe(entity.getTriggerType() == null ? null : entity.getTriggerType().name()),
                    safe(entity.getAudienceType() == null ? null : entity.getAudienceType().name()),
                    safe(entity.getTemplateId() == null ? null : entity.getTemplateId().toString()),
                    safe(template == null ? null : template.getName()),
                    safe(template == null || template.getChannelType() == null ? null : template.getChannelType().name()),
                    safe(template == null ? null : template.getSubjectLine()),
                    safe(template == null ? null : template.getBodyTemplate()),
                    safe(template == null ? null : Boolean.toString(template.isActive())),
                    safe(entity.getNotes())
            );
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hashed) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String allocateCampaignReference(UUID tenantId) {
        int year = java.time.OffsetDateTime.now(ZoneOffset.UTC).getYear();
        Integer sequence = jdbcTemplate.queryForObject(
                """
                insert into carepilot_campaign_reference_counters (tenant_id, reference_year, next_sequence, created_at, updated_at)
                values (?, ?, 1, now(), now())
                on conflict (tenant_id, reference_year)
                do update set next_sequence = carepilot_campaign_reference_counters.next_sequence + 1,
                              updated_at = now()
                returning next_sequence
                """,
                Integer.class,
                tenantId,
                year
        );
        if (sequence == null || sequence < 1) {
            throw new IllegalStateException("Failed to allocate campaign reference");
        }
        return String.format(Locale.ROOT, "CAM-%04d-%06d", year, sequence);
    }

    private CampaignEntity.ActorSnapshot resolveActorSnapshot(UUID tenantId, UUID actorId, String actorRole) {
        if (actorId == null) {
            return new CampaignEntity.ActorSnapshot("System", roleLabel(actorRole), null, null);
        }
        AppUserEntity user = appUserRepository.findByTenantIdAndId(tenantId, actorId).orElse(null);
        if (user == null) {
            return new CampaignEntity.ActorSnapshot("Former user", roleLabel(actorRole), null, null);
        }
        String displayName = firstNonBlank(user.getDisplayName(), user.getEmployeeCode(), user.getUsername());
        if (displayName == null) {
            displayName = "Unknown user";
        }
        return new CampaignEntity.ActorSnapshot(
                displayName,
                roleLabel(actorRole),
                normalizeNullable(user.getEmployeeCode()),
                normalizeNullable(user.getUsername())
        );
    }

    private String roleLabel(String role) {
        if (role == null || role.isBlank()) {
            return "Unknown role";
        }
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "ENGAGE_MANAGER" -> "Engage Manager";
            case "ENGAGE_EXECUTIVE" -> "Engage Executive";
            case "CLINIC_ADMIN" -> "Clinic Admin";
            case "AUDITOR" -> "Auditor";
            case "RECEPTIONIST" -> "Receptionist";
            default -> role.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
