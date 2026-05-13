package com.deepthoughtnet.clinic.carepilot.lead.activity.service;

import com.deepthoughtnet.clinic.carepilot.lead.activity.db.LeadActivityEntity;
import com.deepthoughtnet.clinic.carepilot.lead.activity.db.LeadActivityRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityRecord;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Service for append-only lead activity timeline persistence and queries. */
@Service
public class LeadActivityService {
    private final LeadActivityRepository repository;

    public LeadActivityService(LeadActivityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LeadActivityRecord record(
            UUID tenantId,
            UUID leadId,
            LeadActivityType type,
            String title,
            String description,
            LeadStatus oldStatus,
            LeadStatus newStatus,
            String relatedEntityType,
            UUID relatedEntityId,
            UUID actorId
    ) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(leadId, "leadId");
        if (type == null) throw new IllegalArgumentException("activity type is required");
        if (!StringUtils.hasText(title)) throw new IllegalArgumentException("title is required");

        LeadActivityEntity entity = LeadActivityEntity.create(
                tenantId,
                leadId,
                type,
                title.trim(),
                StringUtils.hasText(description) ? description.trim() : null,
                oldStatus,
                newStatus,
                StringUtils.hasText(relatedEntityType) ? relatedEntityType.trim() : null,
                relatedEntityId,
                actorId
        );
        return toRecord(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<LeadActivityRecord> list(UUID tenantId, UUID leadId, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(leadId, "leadId");
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        return repository.findByTenantIdAndLeadIdOrderByCreatedAtDesc(
                tenantId,
                leadId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Map<UUID, OffsetDateTime> latestByLeadIds(UUID tenantId, Collection<UUID> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByTenantIdAndLeadIdIn(tenantId, leadIds).stream()
                .collect(Collectors.toMap(
                        LeadActivityEntity::getLeadId,
                        LeadActivityEntity::getCreatedAt,
                        (a, b) -> a.isAfter(b) ? a : b
                ));
    }

    @Transactional(readOnly = true)
    public boolean existsScheduleMarker(UUID tenantId, UUID leadId, UUID markerId) {
        return repository.findFirstByTenantIdAndLeadIdAndActivityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                tenantId,
                leadId,
                LeadActivityType.FOLLOW_UP_SCHEDULED,
                markerId
        ).isPresent();
    }

    private LeadActivityRecord toRecord(LeadActivityEntity row) {
        return new LeadActivityRecord(
                row.getId(), row.getTenantId(), row.getLeadId(), row.getActivityType(), row.getTitle(), row.getDescription(),
                row.getOldStatus(), row.getNewStatus(), row.getRelatedEntityType(), row.getRelatedEntityId(), row.getCreatedByAppUserId(), row.getCreatedAt()
        );
    }
}
