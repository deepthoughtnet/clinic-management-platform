package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiPromptTemplateEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiPromptTemplateRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiPromptTemplateRegistryServiceImplTest {
    @Test
    void resolvesTenantOverrideBeforeGlobalDefault() {
        AiPromptTemplateRepository repository = mock(AiPromptTemplateRepository.class);
        UUID tenantId = UUID.randomUUID();
        AiPromptTemplateEntity global = AiPromptTemplateEntity.create(null, "CLINIC",
                "clinic.reconciliation.exception.explain.v1", "v2", AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION.name(),
                "global system", "global user", AiPromptTemplateStatus.ACTIVE.name());
        AiPromptTemplateEntity tenantOverride = AiPromptTemplateEntity.create(tenantId, "CLINIC",
                "clinic.reconciliation.exception.explain.v1", "v3", AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION.name(),
                "tenant system", "tenant user", AiPromptTemplateStatus.ACTIVE.name());
        when(repository.findByTemplateCodeAndStatusOrderByUpdatedAtDesc("clinic.reconciliation.exception.explain.v1", AiPromptTemplateStatus.ACTIVE.name()))
                .thenReturn(List.of(global, tenantOverride));

        AiPromptTemplateDefinition definition = new AiPromptTemplateRegistryServiceImpl(repository, new AiPromptTemplateCatalog())
                .resolve(new AiOrchestrationRequest(
                        AiProductCode.CLINIC,
                        tenantId,
                        UUID.randomUUID(),
                        AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION,
                        "clinic.reconciliation.exception.explain.v1",
                        Map.of(),
                        List.of(),
                        500,
                        0.1d,
                        "corr",
                        "use-case"
                ));

        assertNotNull(definition);
        assertEquals("tenant system", definition.systemPrompt());
        assertEquals("tenant user", definition.userPromptTemplate());
        assertEquals("v3", definition.version());
    }

    @Test
    void fallsBackToCatalogWhenTemplateMissing() {
        AiPromptTemplateRepository repository = mock(AiPromptTemplateRepository.class);
        AiPromptTemplateDefinition definition = new AiPromptTemplateRegistryServiceImpl(repository, new AiPromptTemplateCatalog())
                .resolve(new AiOrchestrationRequest(
                        AiProductCode.GENERIC,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        AiTaskType.SUMMARY,
                        "missing.template",
                        Map.of(),
                        List.of(),
                        200,
                        0.2d,
                        "corr",
                        "use-case"
                ));

        assertEquals("generic.summary.v1", definition.templateCode());
        assertEquals(AiTaskType.SUMMARY, definition.taskType());
    }
}
