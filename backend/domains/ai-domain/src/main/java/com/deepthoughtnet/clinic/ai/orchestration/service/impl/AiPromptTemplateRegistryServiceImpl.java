package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiPromptTemplateEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiPromptTemplateRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AiPromptTemplateRegistryServiceImpl implements AiPromptTemplateRegistryService {
    private final AiPromptTemplateRepository repository;
    private final AiPromptTemplateCatalog catalog;

    public AiPromptTemplateRegistryServiceImpl(AiPromptTemplateRepository repository, AiPromptTemplateCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public AiPromptTemplateDefinition resolve(AiOrchestrationRequest request) {
        if (request == null) {
            return catalog.defaultDefinition(null, null);
        }
        String code = request.promptTemplateCode();
        List<AiPromptTemplateEntity> candidates = code == null || code.isBlank()
                ? List.of()
                : repository.findByTemplateCodeAndStatusOrderByUpdatedAtDesc(code, AiPromptTemplateStatus.ACTIVE.name());
        AiPromptTemplateEntity chosen = candidates.stream()
                .filter(candidate -> matches(candidate, request))
                .sorted(Comparator
                        .comparing((AiPromptTemplateEntity candidate) -> candidate.getTenantId() == null ? 1 : 0)
                        .thenComparing(candidate -> candidate.getProductCode() == null ? 1 : 0)
                        .thenComparing(AiPromptTemplateEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null);

        if (chosen != null) {
            return toDefinition(chosen);
        }
        return catalog.defaultDefinition(request.taskType(), request.promptTemplateCode());
    }

    private boolean matches(AiPromptTemplateEntity entity, AiOrchestrationRequest request) {
        if (entity == null || request == null) {
            return false;
        }
        boolean tenantMatches = entity.getTenantId() == null || Objects.equals(entity.getTenantId(), request.tenantId());
        boolean productMatches = entity.getProductCode() == null
                || Objects.equals(entity.getProductCode(), request.productCode() == null ? null : request.productCode().name());
        boolean taskMatches = entity.getTaskType() == null
                || Objects.equals(entity.getTaskType(), request.taskType() == null ? null : request.taskType().name());
        return tenantMatches && productMatches && taskMatches;
    }

    private AiPromptTemplateDefinition toDefinition(AiPromptTemplateEntity entity) {
        AiPromptTemplateDefinition fallback = catalog.defaultDefinition(
                entity.getTaskType() == null ? null : com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.valueOf(entity.getTaskType()),
                entity.getTemplateCode()
        );
        return new AiPromptTemplateDefinition(
                entity.getTemplateCode(),
                entity.getVersion(),
                entity.getProductCode() == null ? fallback.productCode() : com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode.valueOf(entity.getProductCode()),
                entity.getTaskType() == null ? fallback.taskType() : com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.valueOf(entity.getTaskType()),
                entity.getSystemPrompt(),
                entity.getUserPromptTemplate(),
                AiPromptTemplateStatus.valueOf(entity.getStatus()),
                fallback.fallbackSummary(),
                fallback.fallbackSuggestedActions(),
                fallback.fallbackLimitations()
        );
    }
}
