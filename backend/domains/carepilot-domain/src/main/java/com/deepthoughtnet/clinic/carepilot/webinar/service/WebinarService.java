package com.deepthoughtnet.clinic.carepilot.webinar.service;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarUpsertCommand;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Core webinar CRUD and lifecycle service. */
@Service
public class WebinarService {
    private final WebinarRepository repository;
    private final CampaignRepository campaignRepository;

    public WebinarService(WebinarRepository repository, CampaignRepository campaignRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
    }

    @Transactional(readOnly = true)
    public Page<WebinarRecord> search(UUID tenantId, WebinarSearchCriteria criteria, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        WebinarSearchCriteria safe = criteria == null
                ? new WebinarSearchCriteria(null, null, null, null, null, null)
                : criteria;
        return repository.findAll(spec(tenantId, safe), PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 200)), Sort.by(Sort.Direction.ASC, "scheduledStartAt")))
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<WebinarRecord> find(UUID tenantId, UUID id) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        return repository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Transactional
    public WebinarRecord create(UUID tenantId, WebinarUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        validate(command);
        WebinarEntity row = WebinarEntity.create(tenantId, actorId);
        apply(row, command, actorId);
        return toRecord(repository.save(row));
    }

    @Transactional
    public WebinarRecord update(UUID tenantId, UUID id, WebinarUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        validate(command);
        WebinarEntity row = require(tenantId, id);
        apply(row, command, actorId);
        return toRecord(repository.save(row));
    }

    @Transactional
    public WebinarRecord updateStatus(UUID tenantId, UUID id, WebinarStatus status, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        WebinarEntity row = require(tenantId, id);
        validateTransition(row.getStatus(), status);
        row.setStatus(status);
        row.touch(actorId);
        return toRecord(repository.save(row));
    }

    private WebinarEntity require(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Webinar not found"));
    }

    private void validate(WebinarUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        CarePilotValidators.requireText(command.title(), "title");
        if (command.scheduledStartAt() == null || command.scheduledEndAt() == null) {
            throw new IllegalArgumentException("scheduled window is required");
        }
        if (!command.scheduledEndAt().isAfter(command.scheduledStartAt())) {
            throw new IllegalArgumentException("scheduledEndAt must be after scheduledStartAt");
        }
        if (command.capacity() != null && command.capacity() < 1) {
            throw new IllegalArgumentException("capacity must be positive when configured");
        }
    }

    private void validateTransition(WebinarStatus current, WebinarStatus next) {
        if (current == null || next == null || current == next) {
            return;
        }
        boolean allowed = switch (current) {
            case DRAFT -> next == WebinarStatus.SCHEDULED || next == WebinarStatus.CANCELLED;
            case SCHEDULED -> next == WebinarStatus.LIVE || next == WebinarStatus.CANCELLED;
            case LIVE -> next == WebinarStatus.COMPLETED || next == WebinarStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Invalid webinar status transition: " + current + " -> " + next);
        }
    }

    private void apply(WebinarEntity row, WebinarUpsertCommand command, UUID actorId) {
        row.setTitle(command.title().trim());
        row.setDescription(normalize(command.description()));
        row.setWebinarType(command.webinarType() == null ? WebinarType.OTHER : command.webinarType());
        row.setStatus(command.status() == null ? row.getStatus() : command.status());
        if (command.campaignId() != null && campaignRepository.findByTenantIdAndId(row.getTenantId(), command.campaignId()).isEmpty()) {
            throw new IllegalArgumentException("campaignId does not belong to tenant");
        }
        row.setCampaignId(command.campaignId());
        row.setWebinarUrl(normalize(command.webinarUrl()));
        row.setOrganizerName(normalize(command.organizerName()));
        row.setOrganizerEmail(normalize(command.organizerEmail()));
        row.setScheduledStartAt(command.scheduledStartAt());
        row.setScheduledEndAt(command.scheduledEndAt());
        row.setTimezone(StringUtils.hasText(command.timezone()) ? command.timezone().trim() : "UTC");
        row.setCapacity(command.capacity());
        row.setRegistrationEnabled(command.registrationEnabled() == null || command.registrationEnabled());
        row.setReminderEnabled(command.reminderEnabled() == null || command.reminderEnabled());
        row.setFollowupEnabled(command.followupEnabled() == null || command.followupEnabled());
        row.setTags(normalize(command.tags()));
        row.touch(actorId);
    }

    private WebinarRecord toRecord(WebinarEntity row) {
        CampaignEntity campaign = row.getCampaignId() == null
                ? null
                : campaignRepository.findByTenantIdAndId(row.getTenantId(), row.getCampaignId()).orElse(null);
        return new WebinarRecord(
                row.getId(), row.getTenantId(), row.getTitle(), row.getDescription(), row.getWebinarType(), row.getStatus(),
                row.getCampaignId(), campaign == null ? null : campaign.getName(), row.getWebinarUrl(),
                row.getOrganizerName(), row.getOrganizerEmail(), row.getScheduledStartAt(), row.getScheduledEndAt(), row.getTimezone(), row.getCapacity(),
                row.isRegistrationEnabled(), row.isReminderEnabled(), row.isFollowupEnabled(), row.getTags(), row.getCreatedBy(), row.getUpdatedBy(),
                row.getCreatedAt(), row.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Specification<WebinarEntity> spec(UUID tenantId, WebinarSearchCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.webinarType() != null) {
                predicates.add(cb.equal(root.get("webinarType"), criteria.webinarType()));
            }
            if (criteria.scheduledFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), criteria.scheduledFrom().atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (criteria.scheduledTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStartAt"), criteria.scheduledTo().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)));
            }
            if (Boolean.TRUE.equals(criteria.upcoming())) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), OffsetDateTime.now()));
            }
            if (Boolean.TRUE.equals(criteria.completed())) {
                predicates.add(cb.equal(root.get("status"), WebinarStatus.COMPLETED));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
