package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiToolDefinitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiToolRegistryServiceImpl implements AiToolRegistryService {
    private final AiToolDefinitionRepository repository;

    public AiToolRegistryServiceImpl(AiToolDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ToolRecord> list(UUID tenantId) {
        return repository.findByTenantIdOrTenantIdIsNullOrderByUpdatedAtDesc(tenantId).stream()
                .map(row -> new ToolRecord(
                        row.getId(),
                        row.getTenantId(),
                        row.getToolKey(),
                        row.getName(),
                        row.getDescription(),
                        row.getCategory(),
                        row.isEnabled(),
                        row.getRiskLevel(),
                        row.isRequiresApproval(),
                        row.getInputSchemaJson(),
                        row.getOutputSchemaJson(),
                        row.getUpdatedAt()))
                .toList();
    }
}
