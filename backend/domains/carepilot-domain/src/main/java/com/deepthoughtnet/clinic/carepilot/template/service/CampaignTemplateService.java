package com.deepthoughtnet.clinic.carepilot.template.service;

import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateCreateCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplatePatchCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateRecord;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for campaign template CRUD-like operations. */
@Service
public class CampaignTemplateService {
    private final CampaignTemplateRepository repository;

    public CampaignTemplateService(CampaignTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CampaignTemplateRecord create(UUID tenantId, CampaignTemplateCreateCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireText(command.name(), "name");
        CarePilotValidators.requireText(command.bodyTemplate(), "bodyTemplate");
        CampaignTemplateEntity entity = CampaignTemplateEntity.create(
                tenantId,
                command.name().trim(),
                command.channelType(),
                normalizeNullable(command.subjectLine()),
                command.bodyTemplate().trim(),
                command.active()
        );
        return toRecord(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CampaignTemplateRecord> list(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    public CampaignTemplateRecord patch(UUID tenantId, UUID templateId, CampaignTemplatePatchCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        CampaignTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        entity.patch(normalizeNullable(command.name()), normalizeNullable(command.subjectLine()), normalizeNullable(command.bodyTemplate()), command.active());
        return toRecord(repository.save(entity));
    }

    private CampaignTemplateRecord toRecord(CampaignTemplateEntity entity) {
        return new CampaignTemplateRecord(
                entity.getId(), entity.getTenantId(), entity.getName(), entity.getChannelType(), entity.getSubjectLine(),
                entity.getBodyTemplate(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
