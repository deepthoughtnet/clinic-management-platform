package com.deepthoughtnet.clinic.carepilot.lead.service;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadConvertedMetadataCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPresentationLabels;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Core CRUD/status lifecycle service for CarePilot leads. */
@Service
public class LeadService {
    private static final List<LeadStatus> ACTIVE_PIPELINE_STATUSES = List.of(
            LeadStatus.NEW,
            LeadStatus.CONTACTED,
            LeadStatus.QUALIFIED,
            LeadStatus.FOLLOW_UP_REQUIRED,
            LeadStatus.APPOINTMENT_BOOKED
    );
    private static final List<LeadStatus> FOLLOW_UP_INACTIVE_STATUSES = List.of(LeadStatus.LOST, LeadStatus.SPAM, LeadStatus.CONVERTED);

    private final LeadRepository repository;
    private final CampaignRepository campaignRepository;
    private final LeadActivityService leadActivityService;
    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;

    public LeadService(LeadRepository repository, CampaignRepository campaignRepository, LeadActivityService leadActivityService, PatientRepository patientRepository, AppUserRepository appUserRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.leadActivityService = leadActivityService;
        this.patientRepository = patientRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public Page<LeadRecord> search(UUID tenantId, LeadSearchCriteria criteria, int page, int size) {
        return search(tenantId, ZoneOffset.UTC, criteria, page, size, null, true);
    }

    @Transactional(readOnly = true)
    public Page<LeadRecord> search(UUID tenantId, ZoneId tenantZone, LeadSearchCriteria criteria, int page, int size) {
        return search(tenantId, tenantZone, criteria, page, size, null, true);
    }

    @Transactional(readOnly = true)
    public Page<LeadRecord> search(UUID tenantId, ZoneId tenantZone, LeadSearchCriteria criteria, int page, int size, UUID viewerAppUserId, boolean viewAll) {
        CarePilotValidators.requireTenant(tenantId);
        LeadSearchCriteria safe = criteria == null
                ? new LeadSearchCriteria(null, null, null, null, null, false, false, null, null)
                : criteria;
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        Page<LeadEntity> rows = repository.findAll(spec(tenantId, safe, tenantZone, viewerAppUserId, viewAll), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, OffsetDateTime> latestActivity = leadActivityService.latestByLeadIds(tenantId, rows.map(LeadEntity::getId).toList());
        return rows.map(row -> toRecord(row, latestActivity.get(row.getId())));
    }

    @Transactional(readOnly = true)
    public Optional<LeadRecord> find(UUID tenantId, UUID id) {
        return find(tenantId, id, null, true);
    }

    @Transactional(readOnly = true)
    public Optional<LeadRecord> find(UUID tenantId, UUID id, UUID viewerAppUserId, boolean viewAll) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        Map<UUID, OffsetDateTime> latest = leadActivityService.latestByLeadIds(tenantId, List.of(id));
        return repository.findByTenantIdAndId(tenantId, id).map(row -> {
            assertVisible(row, viewerAppUserId, viewAll);
            return toRecord(row, latest.get(row.getId()));
        });
    }

    @Transactional(readOnly = true)
    public List<LeadRecord> searchAll(UUID tenantId, LeadSearchCriteria criteria) {
        return searchAll(tenantId, ZoneOffset.UTC, criteria, null, true);
    }

    @Transactional(readOnly = true)
    public List<LeadRecord> searchAll(UUID tenantId, ZoneId tenantZone, LeadSearchCriteria criteria) {
        return searchAll(tenantId, tenantZone, criteria, null, true);
    }

    @Transactional(readOnly = true)
    public List<LeadRecord> searchAll(UUID tenantId, ZoneId tenantZone, LeadSearchCriteria criteria, UUID viewerAppUserId, boolean viewAll) {
        CarePilotValidators.requireTenant(tenantId);
        LeadSearchCriteria safe = criteria == null
                ? new LeadSearchCriteria(null, null, null, null, null, false, false, null, null)
                : criteria;
        List<LeadEntity> rows = repository.findAll(spec(tenantId, safe, tenantZone, viewerAppUserId, viewAll), Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, OffsetDateTime> latestActivity = leadActivityService.latestByLeadIds(tenantId, rows.stream().map(LeadEntity::getId).toList());
        return rows.stream().map(row -> toRecord(row, latestActivity.get(row.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public LeadRecord requireVisibleLead(UUID tenantId, UUID id, UUID viewerAppUserId, boolean viewAll) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        LeadEntity row = repository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Lead not found"));
        assertVisible(row, viewerAppUserId, viewAll);
        OffsetDateTime latest = leadActivityService.latestByLeadIds(tenantId, List.of(id)).get(id);
        return toRecord(row, latest);
    }

    @Transactional
    public LeadRecord create(UUID tenantId, LeadUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        validate(command);
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        applyUpsert(entity, command, actorId, false);
        LeadEntity saved = repository.save(entity);
        leadActivityService.record(tenantId, saved.getId(), LeadActivityType.CREATED, "Lead created", "Source: " + LeadPresentationLabels.sourceLabel(saved.getSource()), null, saved.getStatus(), null, null, actorId);
        if (saved.getCampaignId() != null) {
            leadActivityService.record(tenantId, saved.getId(), LeadActivityType.CAMPAIGN_LINKED, "Campaign linked", "Linked campaign", null, null, "CAMPAIGN", saved.getCampaignId(), actorId);
        }
        maybeRecordFollowUpScheduled(saved, null, actorId);
        return toRecord(saved, OffsetDateTime.now());
    }

    @Transactional
    public LeadRecord update(UUID tenantId, UUID id, LeadUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        validate(command);
        LeadEntity entity = require(tenantId, id);
        LeadStatus beforeStatus = entity.getStatus();
        UUID beforeCampaign = entity.getCampaignId();
        OffsetDateTime beforeFollowUp = entity.getNextFollowUpAt();
        boolean converted = entity.getStatus() == LeadStatus.CONVERTED;
        applyUpsert(entity, command, actorId, converted);
        LeadEntity saved = repository.save(entity);
        leadActivityService.record(tenantId, saved.getId(), LeadActivityType.UPDATED, "Lead updated", "Profile details updated", null, null, null, null, actorId);
        if (beforeCampaign == null && saved.getCampaignId() != null) {
            leadActivityService.record(tenantId, saved.getId(), LeadActivityType.CAMPAIGN_LINKED, "Campaign linked", "Linked campaign", null, null, "CAMPAIGN", saved.getCampaignId(), actorId);
        }
        if (beforeStatus != saved.getStatus()) {
            recordStatusTransition(tenantId, saved.getId(), beforeStatus, saved.getStatus(), actorId);
        }
        maybeRecordFollowUpScheduled(saved, beforeFollowUp, actorId);
        return toRecord(saved, OffsetDateTime.now());
    }

    @Transactional
    public LeadRecord updateConvertedMetadata(UUID tenantId, UUID id, LeadConvertedMetadataCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        LeadEntity entity = require(tenantId, id);
        if (entity.getStatus() != LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("Converted lead metadata can only be updated after conversion");
        }
        UUID beforeCampaign = entity.getCampaignId();
        UUID beforeAssignee = entity.getAssignedToAppUserId();
        String beforeNotes = entity.getNotes();
        String beforeTags = entity.getTags();
        String beforeSourceDetails = entity.getSourceDetails();

        if (command.campaignId() != null && campaignRepository.findByTenantIdAndId(entity.getTenantId(), command.campaignId()).isEmpty()) {
            throw new IllegalArgumentException("campaignId does not belong to tenant");
        }
        if (command.assignedToAppUserId() != null) {
            AppUserEntity assignee = appUserRepository.findByTenantIdAndId(entity.getTenantId(), command.assignedToAppUserId())
                    .orElseThrow(() -> new IllegalArgumentException("assignedToAppUserId does not belong to tenant"));
            if (!"ACTIVE".equalsIgnoreCase(assignee.getStatus())) {
                throw new IllegalArgumentException("assignedToAppUserId must reference an active Engage user");
            }
        }

        entity.setNotes(normalizeNullable(command.notes()));
        entity.setTags(normalizeConvertedTags(command.tags()));
        entity.setSourceDetails(normalizeNullable(command.sourceDetails()));
        entity.setCampaignId(command.campaignId());
        entity.setAssignedToAppUserId(command.assignedToAppUserId());
        entity.touch(actorId);

        LeadEntity saved = repository.save(entity);
        if (!Objects.equals(beforeCampaign, saved.getCampaignId())
                || !Objects.equals(beforeAssignee, saved.getAssignedToAppUserId())
                || !Objects.equals(beforeNotes, saved.getNotes())
                || !Objects.equals(beforeTags, saved.getTags())
                || !Objects.equals(beforeSourceDetails, saved.getSourceDetails())) {
            leadActivityService.record(tenantId, saved.getId(), LeadActivityType.UPDATED, "Converted lead updated", "Marketing metadata updated", null, null, null, null, actorId);
        }
        return toRecord(saved, OffsetDateTime.now());
    }

    @Transactional
    public LeadRecord updateStatus(UUID tenantId, UUID id, LeadStatusUpdateCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        LeadEntity entity = require(tenantId, id);
        if (entity.getStatus() == LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("Converted leads cannot be updated through status actions");
        }
        LeadStatus oldStatus = entity.getStatus();
        OffsetDateTime oldFollowUp = entity.getNextFollowUpAt();
        if (command.status() != null) {
            entity.setStatus(command.status());
        }
        if (command.priority() != null) {
            entity.setPriority(command.priority());
        }
        if (command.assignedToAppUserId() != null) {
            entity.setAssignedToAppUserId(command.assignedToAppUserId());
        }
        if (command.lastContactedAt() != null) {
            entity.setLastContactedAt(command.lastContactedAt());
        }
        if (command.nextFollowUpAt() != null) {
            entity.setNextFollowUpAt(command.nextFollowUpAt());
        } else if (oldFollowUp != null && command.status() != null && command.status() != LeadStatus.FOLLOW_UP_REQUIRED) {
            entity.setNextFollowUpAt(null);
        }
        if (StringUtils.hasText(command.comment())) {
            String line = "[" + OffsetDateTime.now(ZoneOffset.UTC) + "] " + command.comment().trim();
            String current = entity.getNotes();
            entity.setNotes(StringUtils.hasText(current) ? current + "\n" + line : line);
            leadActivityService.record(tenantId, entity.getId(), LeadActivityType.NOTE_ADDED, "Note added", command.comment(), null, null, null, null, actorId);
        }
        entity.touch(actorId);
        LeadEntity saved = repository.save(entity);

        if (oldStatus != saved.getStatus()) {
            recordStatusTransition(tenantId, saved.getId(), oldStatus, saved.getStatus(), actorId);
        }
        maybeRecordFollowUpScheduled(saved, oldFollowUp, actorId);
        if (oldFollowUp != null && saved.getNextFollowUpAt() == null) {
            leadActivityService.record(tenantId, saved.getId(), LeadActivityType.FOLLOW_UP_COMPLETED, "Follow-up completed", "Follow-up cleared", null, null, null, null, actorId);
        }
        return toRecord(saved, OffsetDateTime.now());
    }

    @Transactional
    public LeadRecord addNote(UUID tenantId, UUID id, String note, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (!StringUtils.hasText(note)) {
            throw new IllegalArgumentException("note is required");
        }
        LeadEntity entity = require(tenantId, id);
        String line = "[" + OffsetDateTime.now(ZoneOffset.UTC) + "] " + note.trim();
        entity.setNotes(StringUtils.hasText(entity.getNotes()) ? entity.getNotes() + "\n" + line : line);
        entity.touch(actorId);
        LeadEntity saved = repository.save(entity);
        leadActivityService.record(tenantId, saved.getId(), LeadActivityType.NOTE_ADDED, "Note added", note.trim(), null, null, null, null, actorId);
        return toRecord(saved, OffsetDateTime.now());
    }

    @Transactional
    public void linkAppointment(UUID tenantId, UUID leadId, UUID appointmentId, UUID actorId) {
        LeadEntity lead = require(tenantId, leadId);
        lead.setBookedAppointmentId(appointmentId, actorId);
        repository.save(lead);
        leadActivityService.record(tenantId, leadId, LeadActivityType.APPOINTMENT_BOOKED, "Appointment booked", "Linked appointment", null, null, "APPOINTMENT", appointmentId, actorId);
    }

    private LeadEntity require(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Lead not found"));
    }

    private void validate(LeadUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        CarePilotValidators.requireText(command.firstName(), "firstName");
        CarePilotValidators.requireText(command.phone(), "phone");
        if (command.source() == null) {
            throw new IllegalArgumentException("source is required");
        }
        normalizeMobile(command.phone(), "phone");
    }

    private void applyUpsert(LeadEntity entity, LeadUpsertCommand command, UUID actorId, boolean convertedImmutable) {
        if (!convertedImmutable) {
            entity.setFirstName(command.firstName().trim());
            entity.setLastName(normalizeNullable(command.lastName()));
            entity.setPhone(normalizeMobile(command.phone(), "phone"));
            entity.setEmail(normalizeNullable(command.email()));
            entity.setGender(command.gender());
            entity.setDateOfBirth(command.dateOfBirth());
            entity.setSource(command.source() == null ? LeadSource.MANUAL : command.source());
            entity.setSourceDetails(normalizeNullable(command.sourceDetails()));
            if (command.campaignId() != null && campaignRepository.findByTenantIdAndId(entity.getTenantId(), command.campaignId()).isEmpty()) {
                throw new IllegalArgumentException("campaignId does not belong to tenant");
            }
            entity.setCampaignId(command.campaignId());
            entity.setAssignedToAppUserId(command.assignedToAppUserId());
            entity.setStatus(command.status() == null ? entity.getStatus() : command.status());
            entity.setPriority(command.priority() == null ? LeadPriority.MEDIUM : command.priority());
            entity.setLastContactedAt(command.lastContactedAt());
            entity.setNextFollowUpAt(command.nextFollowUpAt());
            entity.setFullName(fullName(command.firstName(), command.lastName()));
        } else {
            validateConvertedLeadUpdate(entity, command);
            if (command.campaignId() != null && campaignRepository.findByTenantIdAndId(entity.getTenantId(), command.campaignId()).isEmpty()) {
                throw new IllegalArgumentException("campaignId does not belong to tenant");
            }
            entity.setSourceDetails(normalizeNullable(command.sourceDetails()));
            entity.setCampaignId(command.campaignId());
            entity.setAssignedToAppUserId(command.assignedToAppUserId());
        }
        entity.setNotes(normalizeNullable(command.notes()));
        entity.setTags(normalizeNullable(command.tags()));
        entity.touch(actorId);
    }

    public void assertVisible(UUID tenantId, UUID id, UUID viewerAppUserId, boolean viewAll) {
        requireVisibleLead(tenantId, id, viewerAppUserId, viewAll);
    }

    private Specification<LeadEntity> spec(UUID tenantId, LeadSearchCriteria c, ZoneId tenantZone, UUID viewerAppUserId, boolean viewAll) {
        ZoneId zone = tenantZone == null ? ZoneOffset.UTC : tenantZone;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!viewAll) {
                if (viewerAppUserId == null) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(cb.or(
                            cb.equal(root.get("assignedToAppUserId"), viewerAppUserId),
                            cb.equal(root.get("createdBy"), viewerAppUserId)
                    ));
                }
            }
            if (c.status() != null) predicates.add(cb.equal(root.get("status"), c.status()));
            if (c.source() != null) predicates.add(cb.equal(root.get("source"), c.source()));
            if (c.assignedToAppUserId() != null) predicates.add(cb.equal(root.get("assignedToAppUserId"), c.assignedToAppUserId()));
            if (c.priority() != null) predicates.add(cb.equal(root.get("priority"), c.priority()));
            if (c.followUpDueOnly()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("nextFollowUpAt"), OffsetDateTime.now(zone)));
                predicates.add(cb.not(root.get("status").in(FOLLOW_UP_INACTIVE_STATUSES)));
            }
            if (c.pipelineOnly()) {
                predicates.add(root.get("status").in(ACTIVE_PIPELINE_STATUSES));
            }
            if (c.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom().atStartOfDay(zone).toOffsetDateTime()));
            }
            if (c.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), c.createdTo().plusDays(1).atStartOfDay(zone).toOffsetDateTime().minusNanos(1)));
            }
            if (StringUtils.hasText(c.search())) {
                String term = "%" + c.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("lastName"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), term),
                        cb.like(cb.lower(root.get("phone")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), term)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void assertVisible(LeadEntity row, UUID viewerAppUserId, boolean viewAll) {
        if (viewAll) {
            return;
        }
        if (viewerAppUserId == null) {
            throw new ForbiddenException("Lead is not visible");
        }
        boolean visible = viewerAppUserId.equals(row.getAssignedToAppUserId()) || viewerAppUserId.equals(row.getCreatedBy());
        if (!visible) {
            throw new ForbiddenException("Lead is not visible");
        }
    }

    private LeadRecord toRecord(LeadEntity row, OffsetDateTime lastActivityAt) {
        OffsetDateTime nextFollowUpAt = row.getStatus() != null && row.getStatus().isTerminal() ? null : row.getNextFollowUpAt();
        UUID convertedPatientId = row.getConvertedPatientId();
        if (convertedPatientId != null && patientRepository.findByTenantIdAndId(row.getTenantId(), convertedPatientId).isEmpty()) {
            convertedPatientId = null;
        }
        return new LeadRecord(
                row.getId(), row.getTenantId(), row.getFirstName(), row.getLastName(), row.getFullName(),
                row.getPhone(), row.getEmail(), row.getGender(), row.getDateOfBirth(), row.getSource(), row.getSourceDetails(),
                row.getCampaignId(), row.getAssignedToAppUserId(), row.getStatus(), row.getPriority(), row.getNotes(), row.getTags(),
                convertedPatientId, row.getBookedAppointmentId(), row.getLastContactedAt(), nextFollowUpAt, lastActivityAt,
                row.getCreatedBy(), row.getUpdatedBy(), row.getCreatedAt(), row.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeConvertedTags(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private String normalizeMobile(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9]{10}")) {
            throw new IllegalArgumentException(field + " must be a valid 10-digit mobile number");
        }
        return normalized;
    }

    private void validateConvertedLeadUpdate(LeadEntity entity, LeadUpsertCommand command) {
        List<String> immutableFields = new ArrayList<>();
        if (!Objects.equals(normalizeNullable(command.firstName()), normalizeNullable(entity.getFirstName()))) immutableFields.add("firstName");
        if (!Objects.equals(normalizeNullable(command.lastName()), normalizeNullable(entity.getLastName()))) immutableFields.add("lastName");
        if (!Objects.equals(normalizeMobile(command.phone(), "phone"), entity.getPhone())) immutableFields.add("phone");
        if (!Objects.equals(normalizeNullable(command.email()), normalizeNullable(entity.getEmail()))) immutableFields.add("email");
        if (!Objects.equals(command.gender(), entity.getGender())) immutableFields.add("gender");
        if (!Objects.equals(command.dateOfBirth(), entity.getDateOfBirth())) immutableFields.add("dateOfBirth");
        if (!Objects.equals(command.source(), entity.getSource())) immutableFields.add("source");
        if (!Objects.equals(command.status(), entity.getStatus())) immutableFields.add("status");
        if (!Objects.equals(command.priority(), entity.getPriority())) immutableFields.add("priority");
        if (!Objects.equals(command.lastContactedAt(), entity.getLastContactedAt())) immutableFields.add("lastContactedAt");
        if (!Objects.equals(command.nextFollowUpAt(), entity.getNextFollowUpAt())) immutableFields.add("nextFollowUpAt");
        if (!immutableFields.isEmpty()) {
            throw new IllegalArgumentException("Converted leads cannot change immutable fields: " + String.join(", ", immutableFields));
        }
    }

    private String fullName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        String value = (f + " " + l).trim();
        return value.isBlank() ? null : value;
    }

    private void recordStatusTransition(UUID tenantId, UUID leadId, LeadStatus oldStatus, LeadStatus newStatus, UUID actorId) {
        LeadActivityType type = LeadActivityType.STATUS_CHANGED;
        String title = "Status updated";
        if (newStatus == LeadStatus.LOST) {
            type = LeadActivityType.LOST;
            title = "Lead marked lost";
        } else if (newStatus == LeadStatus.SPAM) {
            type = LeadActivityType.SPAM_MARKED;
            title = "Lead marked spam";
        }
        leadActivityService.record(tenantId, leadId, type, title, LeadPresentationLabels.statusTransitionLabel(oldStatus, newStatus), oldStatus, newStatus, null, null, actorId);
    }

    private void maybeRecordFollowUpScheduled(LeadEntity saved, OffsetDateTime previousFollowUpAt, UUID actorId) {
        if (saved.getNextFollowUpAt() == null || FOLLOW_UP_INACTIVE_STATUSES.contains(saved.getStatus())) {
            return;
        }
        if (previousFollowUpAt != null && previousFollowUpAt.equals(saved.getNextFollowUpAt())) {
            return;
        }
        UUID marker = UUID.nameUUIDFromBytes((saved.getId().toString() + "|" + saved.getNextFollowUpAt()).getBytes(StandardCharsets.UTF_8));
        if (leadActivityService.existsScheduleMarker(saved.getTenantId(), saved.getId(), marker)) {
            return;
        }
        leadActivityService.record(
                saved.getTenantId(),
                saved.getId(),
                LeadActivityType.FOLLOW_UP_SCHEDULED,
                "Follow-up scheduled",
                "Next follow-up at " + saved.getNextFollowUpAt(),
                null,
                null,
                "FOLLOW_UP",
                marker,
                actorId
        );
    }
}
