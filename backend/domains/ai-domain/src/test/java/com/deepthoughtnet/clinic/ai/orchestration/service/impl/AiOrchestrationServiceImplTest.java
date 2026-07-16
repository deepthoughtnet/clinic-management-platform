package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiGuardrailService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiInvocationLogService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiTaskGenerationConfigService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
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
import java.util.concurrent.atomic.AtomicReference;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class AiOrchestrationServiceImplTest {
    @Test
    void soapTraceLogsUseHashesAndLengthsWithoutRawResponseByDefault() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);
        service.setSoapTraceEnabled(true);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "clinic.consultation.structure-notes.v1",
                Map.of(
                        "consultationId", UUID.randomUUID().toString(),
                        "patientId", UUID.randomUUID().toString(),
                        "chiefComplaint", "Fever, cough, body ache and weakness",
                        "clinicalContextSummary", "Patient snapshot",
                        "clinicalContextJson", "{\"intakeSummary\":{\"latestVitals\":{\"bloodPressureSystolic\":138}}}"
                ),
                List.of(),
                1024,
                0.1d,
                "trace-orch-1",
                "consultation_structure_notes"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.consultation.structure-notes.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "system prompt",
                "{\"subjective\":\"{{input.chiefComplaint}}\",\"objective\":\"Vitals\"}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.CONSULTATION_NOTE_STRUCTURING)).thenReturn(List.of(provider(
                "GEMINI",
                "{\"subjective\":\"Fever\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration\"}",
                AiProviderStatus.AVAILABLE
        )));

        Logger logger = (Logger) LoggerFactory.getLogger(AiOrchestrationServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiOrchestrationResponse response = service.complete(request);

            assertEquals("GEMINI", response.provider());
            assertTrue(response.outputText().contains("Fever"));
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
            assertTrue(messages.contains("SOAP-DRAFT-TRACE stage=PROMPT_RENDERED"));
            assertTrue(messages.contains("SOAP-DRAFT-TRACE stage=PROVIDER_REQUEST"));
            assertTrue(messages.contains("SOAP-DRAFT-TRACE stage=PROVIDER_RESPONSE"));
            assertTrue(messages.contains("traceId=trace-orch-1"));
            assertTrue(messages.contains("promptHash="));
            assertTrue(messages.contains("renderedPromptChars="));
            assertFalse(messages.contains("RAW_RESPONSE_DEBUG"));
            assertFalse(messages.contains("Fever, cough, body ache and weakness"));
            assertFalse(messages.contains("Authorization"));
            assertFalse(messages.contains("api-key"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void soapTraceRawResponseLoggingRequiresExplicitFlag() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);
        service.setSoapTraceEnabled(true);
        service.setSoapTraceRawResponseEnabled(true);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "clinic.consultation.structure-notes.v1",
                Map.of("consultationId", UUID.randomUUID().toString(), "patientId", UUID.randomUUID().toString(), "chiefComplaint", "Fever"),
                List.of(),
                1024,
                0.1d,
                "trace-orch-2",
                "consultation_structure_notes"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.consultation.structure-notes.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "system prompt",
                "{\"subjective\":\"{{input.chiefComplaint}}\"}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.CONSULTATION_NOTE_STRUCTURING)).thenReturn(List.of(provider(
                "GEMINI",
                "{\"subjective\":\"Fever\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration\"}",
                AiProviderStatus.AVAILABLE
        )));

        Logger logger = (Logger) LoggerFactory.getLogger(AiOrchestrationServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.complete(request);

            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
            assertTrue(messages.contains("SOAP-DRAFT-TRACE stage=RAW_RESPONSE_DEBUG"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void soapTruncationRetriesOnceBeforeParsing() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);
        service.setSoapTraceEnabled(true);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "clinic.consultation.structure-notes.v1",
                Map.of(
                        "consultationId", UUID.randomUUID().toString(),
                        "patientId", UUID.randomUUID().toString(),
                        "chiefComplaint", "Fever and cough",
                        "soapClinicalContext", "Patient profile: 44y MALE\nCurrent visit: Fever, cough, body ache and weakness for 4 days\nLatest labs: HbA1c 8.4%"
                ),
                List.of(),
                1024,
                0.1d,
                "trace-orch-trunc",
                "consultation_structure_notes"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.consultation.structure-notes.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "system prompt",
                """
                        SOAP context:
                        {{input.soapClinicalContext}}

                        Return ONLY valid JSON with subjective, objective, assessment, plan.
                        """,
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);

        AtomicReference<Integer> callCount = new AtomicReference<>(0);
        AtomicReference<Integer> firstAttemptMaxTokens = new AtomicReference<>();
        AtomicReference<Integer> retryAttemptMaxTokens = new AtomicReference<>();
        AtomicReference<Boolean> firstAttemptStrictJsonMode = new AtomicReference<>();
        AtomicReference<Boolean> retryAttemptStrictJsonMode = new AtomicReference<>();
        AiProvider provider = new AiProvider() {
            @Override
            public String providerName() {
                return "GEMINI";
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest providerRequest) {
                int nextCount = callCount.updateAndGet(current -> current + 1);
                Integer maxTokens = providerRequest.request() == null ? null : providerRequest.request().maxTokens();
                boolean strictJsonMode = providerRequest.strictJsonMode();
                if (nextCount == 1) {
                    firstAttemptMaxTokens.set(maxTokens);
                    firstAttemptStrictJsonMode.set(strictJsonMode);
                    return new AiProviderResponse(
                            "GEMINI",
                            "model",
                            "{\"subjective\":\"Fever\",\"objective\":\"Temp 38.1 C\"",
                            null,
                            BigDecimal.valueOf(0.91),
                            new AiTokenUsage(25478L, 40L, 25518L, BigDecimal.valueOf(0.12)),
                            "MAX_TOKENS",
                            "TRUNCATED",
                            48,
                            "{\"subjective\":\"Fever\",\"objective\":\"Temp 38.1 C\"",
                            "TRUNCATED"
                    );
                }
                retryAttemptMaxTokens.set(maxTokens);
                retryAttemptStrictJsonMode.set(strictJsonMode);
                return new AiProviderResponse(
                        "GEMINI",
                        "model",
                        "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                        null,
                        BigDecimal.valueOf(0.91),
                        new AiTokenUsage(128L, 96L, 224L, BigDecimal.valueOf(0.12)),
                        "STOP",
                        "COMPLETE",
                        124,
                        "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                        "VALID"
                );
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
        when(router.resolveCandidates(AiTaskType.CONSULTATION_NOTE_STRUCTURING)).thenReturn(List.of(provider));

        Logger logger = (Logger) LoggerFactory.getLogger(AiOrchestrationServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiOrchestrationResponse response = service.complete(request);

            assertEquals("GEMINI", response.provider());
            assertTrue(response.outputText().contains("Fever for 4 days"));
            assertEquals(2, callCount.get());
            assertEquals(Integer.valueOf(1024), firstAttemptMaxTokens.get());
            assertEquals(Integer.valueOf(2048), retryAttemptMaxTokens.get());
            assertTrue(firstAttemptStrictJsonMode.get());
            assertTrue(retryAttemptStrictJsonMode.get());
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
            assertTrue(messages.contains("SOAP-DRAFT-TRACE stage=TRUNCATION"));
            assertTrue(messages.contains("retryCount=1"));
            assertTrue(messages.contains("finalCompletionStatus=COMPLETE"));
            assertFalse(messages.contains("AI response was incomplete. Please retry."));
        } finally {
            logger.detachAppender(appender);
        }
    }
    @Test
    void successPathRoutesThroughProviderAndAuditsRequest() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiProvider provider = provider("GEMINI", "Explain the exception.", AiProviderStatus.AVAILABLE);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

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
    void clampsRequestedMaxTokensToGuardrailLimitBeforeProviderCall() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiGuardrailService guardrailService = mock(AiGuardrailService.class);
        AiInvocationLogService invocationLogService = mock(AiInvocationLogService.class);
        AiTaskGenerationConfigService taskGenerationConfigService = new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096);
        AiOrchestrationServiceImpl service = new AiOrchestrationServiceImpl(
                registry,
                router,
                auditService,
                guardrailService,
                invocationLogService,
                taskGenerationConfigService,
                new ObjectMapper()
        );

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of("chiefComplaint", "Fever, cough, body ache and weakness"),
                List.of(),
                4096,
                0.1d,
                "corr-clamp",
                "clinical_reasoning_generate"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.clinical.reasoning.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CLINICAL_REASONING,
                "system prompt",
                "{\"answer\":\"{{chiefComplaint}}\"}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AtomicReference<Integer> observedMaxTokens = new AtomicReference<>();
        AtomicReference<String> observedModelOverride = new AtomicReference<>();
        AtomicReference<Integer> observedThinkingBudget = new AtomicReference<>();
        AtomicReference<Boolean> observedStrictJson = new AtomicReference<>();
        AiProvider provider = new AiProvider() {
            @Override
            public String providerName() {
                return "GEMINI";
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest providerRequest) {
                observedMaxTokens.set(providerRequest.request() == null ? null : providerRequest.request().maxTokens());
                observedModelOverride.set(providerRequest.modelOverride());
                observedThinkingBudget.set(providerRequest.thinkingBudget());
                observedStrictJson.set(providerRequest.strictJsonMode());
                return new AiProviderResponse(
                        "GEMINI",
                        "gemini-2.5-flash",
                        "{\"answer\":\"ok\"}",
                        null,
                        BigDecimal.ONE,
                        new AiTokenUsage(1L, 1L, 2L, BigDecimal.ONE),
                        "STOP"
                );
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
        when(router.resolveCandidates(AiTaskType.CLINICAL_REASONING)).thenReturn(List.of(provider));
        when(guardrailService.resolveExecutionSettings(any(), anyString(), any(), eq(null)))
                .thenReturn(new AiGuardrailService.ExecutionSettings(4096, 2048, 2048, 120, 30, false));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GEMINI", response.provider());
        assertEquals(2048, observedMaxTokens.get());
        assertEquals("gemini-2.5-flash", observedModelOverride.get());
        assertEquals(0, observedThinkingBudget.get());
        assertTrue(observedStrictJson.get());
        verify(guardrailService, times(1)).resolveExecutionSettings(any(), anyString(), any(), eq(null));
    }

    @Test
    void consultationAskTemplateKeepsPromptBoundedWithoutDuplicatingFullContext() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        String compactContext = "Patient snapshot: Sample Patient\nKnown conditions: Hypertension\nLatest HbA1c: 8.4";
        String hugeContextMarker = "FULL_CONTEXT_BLOCK";
        String hugeContext = hugeContextMarker.repeat(3000);
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.GENERIC_COPILOT,
                "clinic.consultation.ask.v1",
                Map.of(
                        "prompt", "Summarize the longitudinal history",
                        "aiPromptContext", compactContext,
                        "clinicalContextJson", hugeContext,
                        "clinicalContext", Map.of("raw", hugeContext)
                ),
                List.of(),
                1024,
                0.1d,
                "corr-ask",
                "consultation.ask"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.consultation.ask.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.GENERIC_COPILOT,
                "system prompt",
                """
                        Question:
                        {{input.prompt}}

                        Context:
                        {{input.aiPromptContext}}
                        """,
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AtomicReference<String> observedPrompt = new AtomicReference<>();
        AtomicReference<Boolean> observedStrictJson = new AtomicReference<>();
        AtomicReference<Integer> observedThinkingBudget = new AtomicReference<>();
        AiProvider provider = new AiProvider() {
            @Override
            public String providerName() {
                return "GEMINI";
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest providerRequest) {
                observedPrompt.set(providerRequest.userPrompt());
                observedStrictJson.set(providerRequest.strictJsonMode());
                observedThinkingBudget.set(providerRequest.thinkingBudget());
                return new AiProviderResponse(
                        "GEMINI",
                        "gemini-2.5-flash",
                        "{\"answer\":\"ok\"}",
                        null,
                        BigDecimal.ONE,
                        new AiTokenUsage(1L, 1L, 2L, BigDecimal.ONE),
                        "STOP"
                );
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(provider));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GEMINI", response.provider());
        assertNotNull(observedPrompt.get());
        assertTrue(observedPrompt.get().contains("Summarize the longitudinal history"));
        assertTrue(observedPrompt.get().contains(compactContext));
        assertFalse(observedPrompt.get().contains(hugeContextMarker));
        assertTrue(observedPrompt.get().length() < 6000);
        assertEquals(false, observedStrictJson.get());
        assertEquals(Integer.valueOf(0), observedThinkingBudget.get());
    }

    @Test
    void fallbackPathReturnsSafeResponseAndAuditsFallback() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

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
        assertEquals("AI providers are temporarily unavailable. Please retry.", response.outputText());
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
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

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
    void clinicalReasoningRepairFallsBackWhenPrimaryProviderReturnsTruncatedJson() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of("reasoningPrompt", "Return only strict JSON"),
                List.of(),
                null,
                0.1d,
                "corr-repair",
                "clinical_reasoning_generate_repair"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.clinical.reasoning.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CLINICAL_REASONING,
                "Return only strict JSON.",
                "{{input.reasoningPrompt}}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = provider("GEMINI", "{\"confidence\":\"HIGH\"", AiProviderStatus.AVAILABLE, "MAX_TOKENS", "TRUNCATED");
        AiProvider groq = provider("GROQ", "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Community Acquired Pneumonia\",\"confidence\":0.74}}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.CLINICAL_REASONING)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertEquals("VALID", response.parseStatus());
        assertNotNull(response.structuredJson());
        assertTrue(response.structuredJson().contains("\"primaryDiagnosis\""));
    }

    @Test
    void fallsBackFromGeminiToGroqOnQuotaExceeded() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini quota exceeded.", 429);
        AiProvider groq = provider("GROQ", "{\"answer\":\"Groq handled the fallback\"}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertTrue(response.outputText().contains("Groq handled the fallback"));
    }

    @Test
    void fallsBackFromGeminiToGroqOnQuotaExceededForGenericExtraction() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.GENERIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.GENERIC_EXTRACTION,
                "generic.extraction.v1",
                Map.of("message", "Book appointment with Neha Mehta tomorrow morning"),
                List.of(),
                1024,
                0.2d,
                "corr-123",
                "patient-portal-careai-extraction"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "generic.extraction.v1",
                "v1",
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION,
                "system prompt",
                "{\"intent\":\"BOOK_APPOINTMENT\"}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Verify booking details"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini quota exceeded.", 429);
        AiProvider groq = provider("GROQ", "{\"answer\":\"Groq handled extraction fallback\",\"intent\":\"BOOK_APPOINTMENT\"}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.GENERIC_EXTRACTION)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertTrue(response.outputText().contains("Groq handled extraction fallback"));
    }

    @Test
    void clinicalReasoningFallsBackFromGeminiToGroqWithoutExpandingPrompt() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of("reasoningPrompt", "Compact reasoning prompt"),
                List.of(),
                null,
                0.1d,
                "corr-reasoning",
                "clinical_reasoning_generate"
        );
        AiPromptTemplateDefinition template = new AiPromptTemplateDefinition(
                "clinic.clinical.reasoning.v1",
                "v1",
                AiProductCode.CLINIC,
                AiTaskType.CLINICAL_REASONING,
                "system prompt",
                "{{input.reasoningPrompt}}",
                com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus.ACTIVE,
                "fallback summary",
                List.of("Review manually"),
                List.of("Advisory only")
        );
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini quota exceeded.", 429);
        AtomicReference<String> groqModelOverride = new AtomicReference<>();
        AtomicReference<Integer> groqThinkingBudget = new AtomicReference<>();
        AtomicReference<Boolean> groqStrictJson = new AtomicReference<>();
        AiProvider groq = new AiProvider() {
            @Override
            public String providerName() {
                return "GROQ";
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest request) {
                groqModelOverride.set(request == null ? null : request.modelOverride());
                groqThinkingBudget.set(request == null ? null : request.thinkingBudget());
                groqStrictJson.set(request != null && request.strictJsonMode());
                return new AiProviderResponse(
                        "GROQ",
                        "llama-3.1-8b-instant",
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"GROQ\",\"model\":\"llama-3.1-8b-instant\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"GROQ\",\"model\":\"llama-3.1-8b-instant\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        BigDecimal.valueOf(0.88),
                        null,
                        "STOP",
                        "COMPLETE",
                        344,
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"GROQ\",\"model\":\"llama-3.1-8b-instant\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        "VALID"
                );
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
        AiProvider mockProvider = provider("MOCK", "{\"confidence\":\"LOW\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.55,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Mock clinical reasoning fallback.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-clinic-ai\",\"tokens\":{},\"parseStatus\":\"VALID\"}}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.CLINICAL_REASONING)).thenReturn(List.of(gemini, groq, mockProvider));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertEquals(null, groqModelOverride.get());
        assertEquals(null, groqThinkingBudget.get());
        assertEquals(true, groqStrictJson.get());
        assertTrue(response.outputText().contains("Viral Upper Respiratory Infection"));
        assertEquals("llama-3.1-8b-instant", response.model());
    }

    @Test
    void fallsBackFromGeminiToGroqOnTimeout() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini request timed out.", null);
        AiProvider groq = provider("GROQ", "{\"answer\":\"Groq answered after timeout fallback\"}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("GROQ", response.provider());
        assertTrue(response.fallbackUsed());
        assertTrue(response.outputText().contains("Groq answered after timeout fallback"));
    }

    @Test
    void doesNotFallbackOnFatalProviderFailure() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = new AiProvider() {
            @Override
            public String providerName() {
                return "GEMINI";
            }

            @Override
            public boolean supports(AiTaskType taskType) {
                return true;
            }

            @Override
            public AiProviderResponse complete(AiProviderRequest request) {
                throw AiProviderException.fatal("Gemini authorization failed. Check API key/provider configuration.", 403, "GEMINI", "model", "/chat/completions", null);
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
        AiProvider groq = provider("GROQ", "{\"answer\":\"Groq explanation\"}", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini, groq));

        assertThrows(AiProviderException.class, () -> service.complete(request));
    }

    @Test
    void returnsFriendlyFallbackWhenAllProvidersFail() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = failingProvider("GEMINI", "Gemini quota exceeded.", 429);
        AiProvider groq = failingProvider("GROQ", "Groq provider unavailable.", 503);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini, groq));

        AiOrchestrationResponse response = service.complete(request);

        assertTrue(response.fallbackUsed());
        assertEquals(null, response.provider());
        assertEquals("AI providers are temporarily unavailable. Please retry.", response.errorMessage());
        assertEquals("AI providers are temporarily unavailable. Please retry.", response.outputText());
    }

    @Test
    void malformedJsonFallbackExtractsAnswerWhenReadable() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        AiProvider gemini = provider("GEMINI", "{\"answer\":\"You're booked for tomorrow", AiProviderStatus.AVAILABLE);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(gemini));

        AiOrchestrationResponse response = service.complete(request);

        assertEquals("You're booked for tomorrow", response.outputText());
        assertEquals("GEMINI", response.provider());
    }

    @Test
    void coreHasNoClinicRepositoryDependencies() {
        for (Field field : AiOrchestrationServiceImpl.class.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains(".db."));
            assertFalse(typeName.contains(".repository."));
        }
    }

    @Test
    void parsesFencedJsonResponse() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(
                provider("GEMINI", "```json\n{\"summary\":\"Structured summary\",\"followUpSuggestions\":[\"Review in 48h\"]}\n```", AiProviderStatus.AVAILABLE)
        ));

        AiOrchestrationResponse response = service.complete(request);
        assertEquals("Structured summary", response.outputText());
        assertNotNull(response.structuredJson());
        assertTrue(response.suggestedActions().stream().anyMatch(v -> v.contains("Review in 48h")));
    }

    @Test
    void fallsBackToStructuredEnvelopeForPlainTextResponse() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(
                provider("GEMINI", "Here are some possible differential diagnoses...", AiProviderStatus.AVAILABLE)
        ));

        AiOrchestrationResponse response = service.complete(request);
        assertEquals("Here are some possible differential diagnoses...", response.outputText());
        assertNotNull(response.structuredJson());
        assertTrue(response.structuredJson().contains("AI returned unstructured text. Please review carefully."));
        assertTrue(response.limitations().stream().anyMatch(v -> v.contains("unstructured text")));
    }

    @Test
    void handlesEmptyResponseWithSafetyFallback() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(
                provider("GEMINI", "   ", AiProviderStatus.AVAILABLE)
        ));

        AiOrchestrationResponse response = service.complete(request);
        assertNotEquals(null, response.structuredJson());
        assertTrue(response.limitations().stream().anyMatch(v -> v.contains("unstructured text")));
    }

    @Test
    void handlesTruncatedJsonWithRetryFallbackAndNoRawLeak() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(
                provider("GEMINI", "[{\"diagnosis\":\"Gastroenteritis\",", AiProviderStatus.AVAILABLE)
        ));

        AiOrchestrationResponse response = service.complete(request);
        assertEquals("[{\"diagnosis\":\"Gastroenteritis\",", response.outputText());
        assertNotNull(response.structuredJson());
        assertTrue(response.structuredJson().contains("AI returned unstructured text"));
        assertFalse(response.outputText().contains("Sorry, I missed that"));
    }

    @Test
    void parsesTopLevelArrayAndNormalizesAliases() {
        AiPromptTemplateRegistryService registry = mock(AiPromptTemplateRegistryService.class);
        AiProviderRouter router = mock(AiProviderRouter.class);
        AiRequestAuditService auditService = mock(AiRequestAuditService.class);
        AiOrchestrationServiceImpl service = newService(registry, router, auditService);

        AiOrchestrationRequest request = request();
        AiPromptTemplateDefinition template = template();
        when(registry.resolve(request)).thenReturn(template);
        when(router.resolveCandidates(AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION)).thenReturn(List.of(
                provider(
                        "GEMINI",
                        "[{\"condition\":\"Gastroenteritis\",\"reasoning\":\"Likely viral GI syndrome\",\"redFlagExclusions\":[\"severe dehydration\"]}]",
                        AiProviderStatus.AVAILABLE
                )
        ));

        AiOrchestrationResponse response = service.complete(request);
        assertNotNull(response.structuredJson());
        assertTrue(response.structuredJson().contains("\"suggestions\""));
        assertTrue(response.structuredJson().contains("\"diagnosis\":\"Gastroenteritis\""));
        assertTrue(response.structuredJson().contains("\"reason\":\"Likely viral GI syndrome\""));
        assertTrue(response.structuredJson().contains("\"redFlags\":[\"severe dehydration\"]"));
        assertTrue(response.limitations().stream().anyMatch(v -> v.contains("assistive only")));
    }

    private AiPromptTemplateDefinition template() {
        return new AiPromptTemplateDefinition(
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
        return provider(name, outputText, status, null, null);
    }

    private AiProvider provider(String name, String outputText, AiProviderStatus status, String finishReason, String parseStatus) {
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
                        new AiTokenUsage(10L, 5L, 15L, BigDecimal.valueOf(0.12)),
                        finishReason,
                        finishReason == null ? null : com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer.normalize(finishReason),
                        outputText == null ? 0 : outputText.length(),
                        outputText,
                        parseStatus == null ? "UNKNOWN" : parseStatus);
            }

            @Override
            public AiProviderStatus status() {
                return status;
            }
        };
    }

    private AiProvider failingProvider(String name, String message) {
        return failingProvider(name, message, 503);
    }

    private AiProvider failingProvider(String name, String message, Integer statusCode) {
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
                throw AiProviderException.retryable(message, statusCode, name, "model", "/chat/completions", null);
            }

            @Override
            public AiProviderStatus status() {
                return AiProviderStatus.AVAILABLE;
            }
        };
    }

    private AiOrchestrationServiceImpl newService(AiPromptTemplateRegistryService registry,
                                                  AiProviderRouter router,
                                                  AiRequestAuditService auditService) {
        AiGuardrailService guardrailService = mock(AiGuardrailService.class);
        when(guardrailService.resolveExecutionSettings(any(), anyString(), any(), eq(null)))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(1);
                    AiOrchestrationRequest request = invocation.getArgument(2);
                    int promptChars = prompt == null ? 0 : prompt.length();
                    int guardrailLimit = 2048;
                    Integer requested = request == null ? null : request.maxTokens();
                    int effective = requested == null ? guardrailLimit : Math.min(requested, guardrailLimit);
                    return new AiGuardrailService.ExecutionSettings(requested, guardrailLimit, effective, promptChars, Math.max(1, (promptChars + 3) / 4), false);
                });
        return new AiOrchestrationServiceImpl(
                registry,
                router,
                auditService,
                guardrailService,
                mock(AiInvocationLogService.class),
                new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096),
                new ObjectMapper()
        );
    }

    private AiOrchestrationServiceImpl newService(AiPromptTemplateRegistryService registry,
                                                  AiProviderRouter router,
                                                  AiRequestAuditService auditService,
                                                  AiGuardrailService guardrailService) {
        return new AiOrchestrationServiceImpl(
                registry,
                router,
                auditService,
                guardrailService,
                mock(AiInvocationLogService.class),
                new AiTaskGenerationConfigService(null, "gemini-2.5-flash", 0, true, 2048, 4096),
                new ObjectMapper()
        );
    }
}
