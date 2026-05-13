package com.deepthoughtnet.clinic.carepilot.lead.service;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final List<LeadStatus> FOLLOW_UP_INACTIVE_STATUSES = List.of(LeadStatus.LOST, LeadStatus.SPAM, LeadStatus.CONVERTED);

    private final LeadRepository repository;
    private final CampaignRepository campaignRepository;
    private final LeadActivityService leadActivityService;

    public LeadService(LeadRepository repository, CampaignRepository campaignRepository, LeadActivityService leadActivityService) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.leadActivityService = leadActivityService;
    }

    @Transactional(readOnly = true)
    public Page<LeadRecord> search(UUID tenantId, LeadSearchCriteria criteria, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        LeadSearchCriteria safe = criteria == null
                ? new LeadSearchCriteria(null, null, null, null, null, false, null, null)
                : criteria;
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        Page<LeadEntity> rows = repository.findAll(spec(tenantId, safe), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, OffsetDateTime> latestActivity = leadActivityService.latestByLeadIds(tenantId, rows.map(LeadEntity::getId).toList());
        return rows.map(row -> toRecord(row, latestActivity.get(row.getId())));
    }

    @Transactional(readOnly = true)
    public Optional<LeadRecord> find(UUID tenantId, UUID id) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        Map<UUID, OffsetDateTime> latest = leadActivityService.latestByLeadIds(tenantId, List.of(id));
        return repository.findByTenantIdAndId(tenantId, id).map(row -> toRecord(row, latest.get(row.getId())));
    }

    @Transactional
    public LeadRecord create(UUID tenantId, LeadUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        validate(command);
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        applyUpsert(entity, command, actorId, false);
        LeadEntity saved = repository.save(entity);
        leadActivityService.record(tenantId, saved.getId(), LeadActivityType.CREATED, "Lead created", saved.getSource().name(), null, saved.getStatus(), null, null, actorId);
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
    public LeadRecord updateStatus(UUID tenantId, UUID id, LeadStatusUpdateCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        LeadEntity entity = require(tenantId, id);
        LeadStatus oldStatus = entity.getStatus();
        OffsetDateTime oldFollowUp = entity.getNextFollowUpAt();
        if (entity.getStatus() == LeadStatus.CONVERTED && command.status() != null && command.status() != LeadStatus.CONVERTED) {
            throw new IllegalArgumentException("Converted leads cannot be transitioned out of CONVERTED");
        }
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
        String phone = command.phone().trim();
        if (!phone.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("phone must be a valid phone number");
        }
    }

    private void applyUpsert(LeadEntity entity, LeadUpsertCommand command, UUID actorId, boolean convertedImmutable) {
        if (!convertedImmutable) {
            entity.setFirstName(command.firstName().trim());
            entity.setLastName(normalizeNullable(command.lastName()));
            entity.setPhone(command.phone().trim());
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
        }
        entity.setNotes(normalizeNullable(command.notes()));
        entity.setTags(normalizeNullable(command.tags()));
        entity.touch(actorId);
    }

    private Specification<LeadEntity> spec(UUID tenantId, LeadSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (c.status() != null) predicates.add(cb.equal(root.get("status"), c.status()));
            if (c.source() != null) predicates.add(cb.equal(root.get("source"), c.source()));
            if (c.assignedToAppUserId() != null) predicates.add(cb.equal(root.get("assignedToAppUserId"), c.assignedToAppUserId()));
            if (c.priority() != null) predicates.add(cb.equal(root.get("priority"), c.priority()));
            if (c.followUpDueOnly()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("nextFollowUpAt"), OffsetDateTime.now()));
                predicates.add(cb.not(root.get("status").in(FOLLOW_UP_INACTIVE_STATUSES)));
            }
            if (c.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom().atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (c.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), c.createdTo().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)));
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

    private LeadRecord toRecord(LeadEntity row, OffsetDateTime lastActivityAt) {
        return new LeadRecord(
                row.getId(), row.getTenantId(), row.getFirstName(), row.getLastName(), row.getFullName(),
                row.getPhone(), row.getEmail(), row.getGender(), row.getDateOfBirth(), row.getSource(), row.getSourceDetails(),
                row.getCampaignId(), row.getAssignedToAppUserId(), row.getStatus(), row.getPriority(), row.getNotes(), row.getTags(),
                row.getConvertedPatientId(), row.getBookedAppointmentId(), row.getLastContactedAt(), row.getNextFollowUpAt(), lastActivityAt,
                row.getCreatedBy(), row.getUpdatedBy(), row.getCreatedAt(), row.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
        leadActivityService.record(tenantId, leadId, type, title, oldStatus + " -> " + newStatus, oldStatus, newStatus, null, null, actorId);
        if (oldStatus == LeadStatus.FOLLOW_UP_REQUIRED && newStatus != LeadStatus.FOLLOW_UP_REQUIRED) {
            leadActivityService.record(tenantId, leadId, LeadActivityType.FOLLOW_UP_COMPLETED, "Follow-up completed", "Status advanced out of follow-up", oldStatus, newStatus, null, null, actorId);
        }
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
