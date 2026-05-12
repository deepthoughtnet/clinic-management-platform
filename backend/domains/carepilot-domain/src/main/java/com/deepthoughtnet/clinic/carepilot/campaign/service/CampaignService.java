package com.deepthoughtnet.clinic.carepilot.campaign.service;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for campaign definition lifecycle management. */
@Service
public class CampaignService {
    private final CampaignRepository repository;

    public CampaignService(CampaignRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CampaignRecord create(UUID tenantId, CampaignCreateCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireText(command.name(), "name");
        CampaignEntity entity = CampaignEntity.create(
                tenantId,
                command.name().trim(),
                command.campaignType() == null ? CampaignType.CUSTOM : command.campaignType(),
                command.triggerType() == null ? TriggerType.MANUAL : command.triggerType(),
                command.audienceType() == null ? AudienceType.ALL_PATIENTS : command.audienceType(),
                command.templateId(),
                normalizeNullable(command.notes()),
                actorId
        );
        return toRecord(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CampaignRecord> list(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CampaignRecord> find(UUID tenantId, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        return repository.findByTenantIdAndId(tenantId, campaignId).map(this::toRecord);
    }

    @Transactional
    public CampaignRecord activate(UUID tenantId, UUID campaignId) {
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        entity.activate();
        return toRecord(repository.save(entity));
    }

    @Transactional
    public CampaignRecord deactivate(UUID tenantId, UUID campaignId) {
        CampaignEntity entity = requireCampaign(tenantId, campaignId);
        entity.deactivate();
        return toRecord(repository.save(entity));
    }

    private CampaignEntity requireCampaign(UUID tenantId, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        return repository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
    }

    private CampaignRecord toRecord(CampaignEntity entity) {
        return new CampaignRecord(
                entity.getId(), entity.getTenantId(), entity.getName(), entity.getCampaignType(), entity.getStatus(),
                entity.getTriggerType(), entity.getAudienceType(), entity.getTemplateId(), entity.isActive(),
                entity.getNotes(), entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
