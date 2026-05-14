package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiGuardrailProfileQueryServiceImpl implements AiGuardrailProfileQueryService {
    private final AiGuardrailProfileRepository repository;

    public AiGuardrailProfileQueryServiceImpl(AiGuardrailProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<GuardrailProfileRecord> list(UUID tenantId) {
        return repository.findAll().stream()
                .filter(row -> row.getTenantId() == null || row.getTenantId().equals(tenantId))
                .map(row -> new GuardrailProfileRecord(
                        row.getId(), row.getTenantId(), row.getProfileKey(), row.getName(), row.getDescription(),
                        row.isEnabled(), row.getBlockedTopicsJson(), row.isPiiRedactionEnabled(),
                        row.isHumanApprovalRequired(), row.getMaxOutputTokens(), row.getUpdatedAt()))
                .toList();
    }
}
