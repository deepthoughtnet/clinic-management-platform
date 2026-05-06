package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiOrchestrationServiceImplTest {
    @Test
    void successPathRoutesThroughProviderAndAuditsRequest() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiProvider provider = provider("GEMINI", "Explain the exception.", AiProviderStatus.AVAILABLE);
        AiOrchestrationServiceImpl service = new AiOrchestrationServiceImpl(registry, router, auditService, new ObjectMapper());

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.reconciliation.exception.explain.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION,
                "system prompt",
                "Answer {{evidenceSummary}}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Check the statement"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(provider));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("Explain the exception.", response.outputText());
        assertEquals("GEMINI", response.provider());
        assertFalse(response.fallbackUsed());
        assertNotNull(response.auditId());
        ArgumentCaptor<AiRequestAuditCommand> captor = ArgumentCaptor.forClass(AiRequestAuditCommand.class);
        verify(auditService).record(captor.capture());
        assertEquals("SUCCESS", captor.getValue().status());
        assertEquals("CLINIC", captor.getValue().productCode());
        assertEquals("RECONCILIATION_EXCEPTION_EXPLANATION", captor.getValue().taskType());
        assertEquals("clinic.reconciliation.exception.explain.v1", captor.getValue().promptTemplateCode());
    }

    @Test
    void fallbackPathReturnsSafeResponseAndAuditsFallback() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = new AiOrchestrationServiceImpl(registry, router, auditService, new ObjectMapper());

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "generic.summary.v1",
                "v1",
                AiProductCode.GENERIC,
                AiTaskType.SUMMARY,
                "system prompt",
                "Answer {{evidenceSummary}}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.SUMMARY)).thenReturn(List.of());

        AiOrchestrationResponse response = service.complete(request);

        assertTrue(response.fallbackUsed());
        assertTrue(response.outputText().contains("fallback summary"));
        assertEquals(null, response.provider());
        ArgumentCaptor<AiRequestAuditCommand> captor = ArgumentCaptor.forClass(AiRequestAuditCommand.class);
        verify(auditService).record(captor.capture());
        assertEquals("FALLBACK", captor.getValue().status());
        assertTrue(captor.getValue().fallbackUsed());
    }

    @Test
    void fallsBackFromGeminiToGroqWhenPrimaryProviderFails() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = new AiOrchestrationServiceImpl(registry, router, auditService, new ObjectMapper());

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.reconciliation.exception.explain.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION,
                "system prompt",
                "{\"answer\":\"{{evidenceSummary}}\",\"suggestedActions\":[\"Review manually\"],\"limitations\":[\"Advisory only\"],\"confidence\":0.88}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini timed out");
        AiProvider groq = provider("GROQ", "{\"answer\":\"Groq explanation\",\"suggestedActions\":[\"Open manual match\"],\"limitations\":[\"Fallback provider was used. Please verify before acting.\"],\"confidence\":0.82}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertTrue(response.outputText().contains("Groq explanation"));
        assertTrue(response.limitations().stream().anyMatch(value -> value.contains("Fallback provider was used")));
        ArgumentCaptor<AiRequestAuditCommand> captor = ArgumentCaptor.forClass(AiRequestAuditCommand.class);
        verify(auditService).record(captor.capture());
        assertEquals("FALLBACK", captor.getValue().status());
        assertEquals("GROQ", captor.getValue().provider());
        assertTrue(captor.getValue().fallbackUsed());
        assertTrue(captor.getValue().errorMessage().contains("Gemini timed out"));
    }

    @Test
    void coreHasNoClinicRepositoryDependencies() {
        for (Field field : AiOrchestrationServiceImpl.class.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains(".db."));
            assertFalse(typeName.contains(".repository."));
        }
    }

    private AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION,
                "clinic.reconciliation.exception.explain.v1",
                Map.of("statementReference", "STAT-001"),
                List.of(new AiEvidenceReference(AiProductCode.CLINIC, UUID.randomUUID(), "STATEMENT_LINE",
                        UUID.randomUUID(), "STAT-001", "Statement evidence", Map.of())),
                800,
                0.2d,
                "corr-1",
                "use-case-1"
        );
    }

    private AiProvider provider(String name, String outputText, AiProviderStatus status) {
        return new AiProvider() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest request) {
                return new AiProviderResponse(name, "model", outputText, null, BigDecimal.valueOf(0.91),
                        new AiTokenUsage(10L, 5L, 15L, BigDecimal.valueOf(0.12)));
            }

            @Override
            public AiProviderStatus status() {
                return status;
            }
        };
    }

    private AiProvider failingProvider(String name, String message) {
        return new AiProvider() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest request) {
                throw new IllegalStateException(message);
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
    }
}
