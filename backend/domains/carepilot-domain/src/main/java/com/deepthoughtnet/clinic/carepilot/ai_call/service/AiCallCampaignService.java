package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallCampaignUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Core CRUD/lifecycle service for AI call campaigns. */
@Service
public class AiCallCampaignService {
    private final AiCallCampaignRepository repository;

    public AiCallCampaignService(AiCallCampaignRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AiCallCampaignRecord> list(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantId(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AiCallCampaignRecord> find(UUID tenantId, UUID id) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        return repository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Transactional
    public AiCallCampaignRecord create(UUID tenantId, AiCallCampaignUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        validate(command);
        AiCallCampaignEntity row = AiCallCampaignEntity.create(tenantId, actorId);
        apply(row, command, actorId);
        return toRecord(repository.save(row));
    }

    @Transactional
    public AiCallCampaignRecord update(UUID tenantId, UUID id, AiCallCampaignUpsertCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        validate(command);
        AiCallCampaignEntity row = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("AI call campaign not found"));
        apply(row, command, actorId);
        return toRecord(repository.save(row));
    }

    @Transactional
    public AiCallCampaignRecord updateStatus(UUID tenantId, UUID id, AiCallCampaignStatus status, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(id, "id");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        AiCallCampaignEntity row = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("AI call campaign not found"));
        row.setStatus(status);
        row.touch(actorId);
        return toRecord(repository.save(row));
    }

    private void validate(AiCallCampaignUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        CarePilotValidators.requireText(command.name(), "name");
        if (command.maxAttempts() != null && command.maxAttempts() < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    private void apply(AiCallCampaignEntity row, AiCallCampaignUpsertCommand command, UUID actorId) {
        row.setName(command.name().trim());
        row.setDescription(normalize(command.description()));
        row.setCallType(command.callType() == null ? row.getCallType() : command.callType());
        row.setStatus(command.status() == null ? row.getStatus() : command.status());
        row.setTemplateId(command.templateId());
        row.setChannel(command.channel() == null ? ChannelType.SMS : command.channel());
        row.setRetryEnabled(command.retryEnabled() == null || command.retryEnabled());
        row.setMaxAttempts(command.maxAttempts() == null ? 3 : command.maxAttempts());
        row.setEscalationEnabled(command.escalationEnabled() != null && command.escalationEnabled());
        row.touch(actorId);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AiCallCampaignRecord toRecord(AiCallCampaignEntity row) {
        return new AiCallCampaignRecord(
                row.getId(), row.getTenantId(), row.getName(), row.getDescription(), row.getCallType(), row.getStatus(), row.getTemplateId(),
                row.getChannel(), row.isRetryEnabled(), row.getMaxAttempts(), row.isEscalationEnabled(), row.getCreatedBy(), row.getUpdatedBy(),
                row.getCreatedAt(), row.getUpdatedAt()
        );
    }
}
