package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiGuardrailService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiInvocationLogService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiTaskGenerationConfigService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOrchestrationServiceImpl implements AiOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationServiceImpl.class);
    private static final Pattern ANSWER_FIELD_PATTERN = Pattern.compile("\"answer\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private final AiPromptTemplateRegistryService templateRegistry;
    private final AiProviderRouter providerRouter;
    private final AiRequestAuditService auditService;
    private final AiGuardrailService guardrailService;
    private final AiInvocationLogService invocationLogService;
    private final AiTaskGenerationConfigService taskGenerationConfigService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationServiceImpl(AiPromptTemplateRegistryService templateRegistry,
                                      AiProviderRouter providerRouter,
                                      AiRequestAuditService auditService,
                                      AiGuardrailService guardrailService,
                                      AiInvocationLogService invocationLogService,
                                      AiTaskGenerationConfigService taskGenerationConfigService,
                                      ObjectMapper objectMapper) {
        this.templateRegistry = templateRegistry;
        this.providerRouter = providerRouter;
        this.auditService = auditService;
        this.guardrailService = guardrailService;
        this.invocationLogService = invocationLogService;
        this.taskGenerationConfigService = taskGenerationConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiOrchestrationResponse complete(AiOrchestrationRequest request) {
        long started = System.currentTimeMillis();
        UUID requestId = UUID.randomUUID();
        AiPromptTemplateDefinition template = templateRegistry.resolve(request);
        AiTaskGenerationConfigService.GenerationConfig generationConfig = taskGenerationConfigService.resolve(
                request.taskType(),
                request.promptTemplateCode(),
                request.useCaseCode()
        );
        AiOrchestrationRequest requestWithTaskDefaults = applyGenerationConfig(request, generationConfig);
        Map<String, Object> renderedVariables = renderVariables(requestWithTaskDefaults, template);
        String evidenceSummary = summarizeEvidence(request.evidence());
        String userPrompt = render(template.userPromptTemplate(), renderedVariables, evidenceSummary);
        AiGuardrailService.ExecutionSettings executionSettings = guardrailService.resolveExecutionSettings(request.tenantId(), userPrompt, requestWithTaskDefaults, null);
        boolean strictJson = requiresStrictJson(template);
        String schemaMode = executionSettings.compactMode()
                ? "COMPACT_JSON"
                : (strictJson ? "STRICT_JSON" : "FREEFORM");
        List<AiProvider> candidates = providerRouter.resolveCandidates(request.taskType());
        AiProvider candidateForLog = candidates.isEmpty() ? null : candidates.get(0);
        String fallbackOrder = buildFallbackOrder(candidates);
        log.info("[AI-REQUEST] taskType={} provider={} resolvedModel={} requestedMaxTokens={} effectiveMaxTokens={} guardrailLimit={} temperature={} topP={} schemaMode={} compactMode={} promptChars={} estimatedPromptTokens={} thinkingBudget={} strictJsonMode={} fallbackOrder={}",
                request.taskType(),
                candidateForLog == null ? null : candidateForLog.providerName(),
                generationConfig.modelOverride(),
                executionSettings.requestedMaxTokens(),
                executionSettings.effectiveMaxTokens(),
                executionSettings.guardrailLimit(),
                requestWithTaskDefaults.temperature(),
                null,
                schemaMode,
                executionSettings.compactMode(),
                executionSettings.promptChars(),
                executionSettings.estimatedPromptTokens(),
                generationConfig.thinkingBudget(),
                generationConfig.strictJsonMode(),
                fallbackOrder);
        AiOrchestrationRequest executionRequest = applyMaxTokens(requestWithTaskDefaults, executionSettings.effectiveMaxTokens());
        renderedVariables = renderVariables(executionRequest, template);
        userPrompt = render(template.userPromptTemplate(), renderedVariables, evidenceSummary);
        guardrailService.validatePreExecution(request.tenantId(), userPrompt, executionRequest, null);

        AiProvider provider = null;
        AiProviderResponse providerResponse = null;
        AiProviderException lastRetryableFailure = null;
        AiProviderException lastFailure = null;
        boolean fallbackUsed = false;
        AiOrchestrationResponse response = null;
        for (int i = 0; i < candidates.size(); i++) {
            AiProvider candidate = candidates.get(i);
            AiProviderRequest providerRequest = new AiProviderRequest(
                    executionRequest,
                    template.version(),
                    template.systemPrompt(),
                    userPrompt,
                    renderedVariables,
                    request.evidence(),
                    requestId,
                    "GEMINI".equalsIgnoreCase(candidate.providerName()) ? generationConfig.modelOverride() : null,
                    "GEMINI".equalsIgnoreCase(candidate.providerName()) ? generationConfig.thinkingBudget() : null,
                    generationConfig.strictJsonMode()
            );
            long providerStarted = System.currentTimeMillis();
            log.info("AI provider attempt. requestId={}, provider={}, attempt={}, taskType={}",
                    requestId, candidate.providerName(), i + 1, request.taskType());
            try {
                providerResponse = candidate.complete(providerRequest);
                if (providerResponse == null) {
                    throw new IllegalStateException("Provider returned null response");
                }
                provider = candidate;
                fallbackUsed = i > 0;
                if (fallbackUsed) {
                    log.info("AI provider fallback selected. requestId={}, provider={}, attempt={}, taskType={}",
                            requestId, candidate.providerName(), i + 1, request.taskType());
                }
                log.info("AI provider completed. requestId={}, provider={}, latencyMs={}, responseChars={}, finishReason={}, normalizedFinishReason={}",
                        requestId,
                        candidate.providerName(),
                        System.currentTimeMillis() - providerStarted,
                        providerResponse == null || providerResponse.responseChars() == null ? 0 : providerResponse.responseChars(),
                        providerResponse == null ? null : providerResponse.finishReason(),
                        providerResponse == null ? null : providerResponse.normalizedFinishReason());
                ParsedOutput parsed = parseProviderOutput(providerResponse, template);
                if (shouldAdvanceToNextProvider(executionRequest, parsed)) {
                    log.warn("AI provider returned incomplete structured output. requestId={}, provider={}, attempt={}, taskType={}, parseStatus={}, finishReason={}, useCaseCode={}",
                            requestId,
                            candidate.providerName(),
                            i + 1,
                            executionRequest.taskType(),
                            parsed.parseStatus(),
                            parsed.normalizedFinishReason(),
                            executionRequest.useCaseCode());
                    lastFailure = AiProviderException.retryable(
                            parsed.errorMessage() == null ? "AI provider returned incomplete structured output." : parsed.errorMessage(),
                            null,
                            candidate.providerName(),
                            null,
                            null,
                            null
                    );
                    lastRetryableFailure = lastFailure;
                    provider = candidate;
                    continue;
                }
                response = toResponse(executionRequest, requestId, provider, providerResponse, template, request.evidence(),
                        started, fallbackUsed, fallbackUsed ? "Fallback provider was used. Please verify before acting." : null, parsed);
                break;
            } catch (AiProviderException ex) {
                lastFailure = ex;
                log.warn("AI provider failed. requestId={}, provider={}, retryable={}, status={}, latencyMs={}, error={}",
                        requestId,
                        candidate.providerName(),
                        ex.retryable(),
                        ex.statusCode(),
                        System.currentTimeMillis() - providerStarted,
                        safeMessage(ex));
                if (!ex.retryable()) {
                    throw ex;
                }
                lastRetryableFailure = ex;
            } catch (RuntimeException ex) {
                log.warn("AI provider failed. requestId={}, provider={}, latencyMs={}, error={}",
                        requestId,
                        candidate.providerName(),
                        System.currentTimeMillis() - providerStarted,
                        safeMessage(ex));
                lastRetryableFailure = AiProviderException.retryable(
                        safeMessage(ex),
                        null,
                        candidate.providerName(),
                        null,
                        null,
                        ex
                );
                lastFailure = lastRetryableFailure;
            }
        }

        if (response == null) {
            fallbackUsed = true;
            String errorMessage = "AI providers are temporarily unavailable. Please retry.";
            response = fallbackResponse(request, requestId, template, evidenceSummary, started, errorMessage);
            lastFailure = lastRetryableFailure == null ? lastFailure : lastRetryableFailure;
        }

        auditService.record(new AiRequestAuditCommand(
                requestId,
                requestWithTaskDefaults.productCode() == null ? AiProductCode.GENERIC.name() : requestWithTaskDefaults.productCode().name(),
                requestWithTaskDefaults.tenantId(),
                requestWithTaskDefaults.actorUserId(),
                requestWithTaskDefaults.useCaseCode(),
                requestWithTaskDefaults.taskType() == null ? AiTaskType.GENERIC_COPILOT.name() : requestWithTaskDefaults.taskType().name(),
                template.templateCode(),
                template.version(),
                provider == null ? null : provider.providerName(),
                providerResponse == null ? null : providerResponse.model(),
                requestHash(request, template, evidenceSummary),
                inputSummary(request, template, renderedVariables, evidenceSummary),
                outputSummary(response.outputText()),
                fallbackUsed ? "FALLBACK" : "SUCCESS",
                response.confidence(),
                response.latencyMs(),
                tokenUsage(response.tokenUsage(), true),
                tokenUsage(response.tokenUsage(), false),
                response.tokenUsage() == null ? null : response.tokenUsage().totalTokens(),
                response.tokenUsage() == null ? null : response.tokenUsage().estimatedCost(),
                fallbackUsed || response.fallbackUsed(),
                lastFailure == null ? response.errorMessage() : safeMessage(lastFailure),
                requestWithTaskDefaults.correlationId()
        ));
        invocationLogService.record(new AiInvocationLogService.InvocationLogCommand(
                requestWithTaskDefaults.tenantId(),
                requestId,
                requestWithTaskDefaults.correlationId(),
                requestWithTaskDefaults.productCode() == null ? AiProductCode.GENERIC.name() : requestWithTaskDefaults.productCode().name(),
                requestWithTaskDefaults.useCaseCode(),
                template.templateCode(),
                parseVersionNumber(template.version()),
                provider == null ? null : provider.providerName(),
                providerResponse == null ? null : providerResponse.model(),
                fallbackUsed ? "FALLBACK" : "SUCCESS",
                tokenUsage(response.tokenUsage(), true),
                tokenUsage(response.tokenUsage(), false),
                response.tokenUsage() == null ? null : response.tokenUsage().estimatedCost(),
                response.latencyMs(),
                inputSummary(request, template, renderedVariables, evidenceSummary),
                outputSummary(response.outputText()),
                lastFailure == null ? null : "PROVIDER_ERROR",
                lastFailure == null ? response.errorMessage() : safeMessage(lastFailure),
                requestWithTaskDefaults.actorUserId()
        ));
        return response;
    }

    private AiOrchestrationRequest applyMaxTokens(AiOrchestrationRequest request, Integer maxTokens) {
        if (request == null) {
            return null;
        }
        if (request.maxTokens() != null && request.maxTokens().equals(maxTokens)) {
            return request;
        }
        return new AiOrchestrationRequest(
                request.productCode(),
                request.tenantId(),
                request.actorUserId(),
                request.taskType(),
                request.promptTemplateCode(),
                request.inputVariables(),
                request.evidence(),
                maxTokens,
                request.temperature(),
                request.correlationId(),
                request.useCaseCode()
        );
    }

    private AiOrchestrationRequest applyGenerationConfig(AiOrchestrationRequest request,
                                                         AiTaskGenerationConfigService.GenerationConfig generationConfig) {
        if (request == null || generationConfig == null) {
            return request;
        }
        Integer configuredMaxTokens = generationConfig.maxOutputTokens();
        Integer nextMaxTokens = request.maxTokens();
        if (configuredMaxTokens != null && (nextMaxTokens == null || nextMaxTokens > configuredMaxTokens)) {
            nextMaxTokens = configuredMaxTokens;
        }
        boolean unchanged = request.maxTokens() == null ? nextMaxTokens == null : request.maxTokens().equals(nextMaxTokens);
        if (unchanged) {
            return request;
        }
        return new AiOrchestrationRequest(
                request.productCode(),
                request.tenantId(),
                request.actorUserId(),
                request.taskType(),
                request.promptTemplateCode(),
                request.inputVariables(),
                request.evidence(),
                nextMaxTokens,
                request.temperature(),
                request.correlationId(),
                request.useCaseCode()
        );
    }

    private String buildFallbackOrder(List<AiProvider> candidates) {
        List<String> order = new ArrayList<>();
        if (candidates != null) {
            for (AiProvider provider : candidates) {
                if (provider == null || provider.providerName() == null || provider.providerName().isBlank()) {
                    continue;
                }
                String normalized = provider.providerName().trim().toLowerCase(java.util.Locale.ROOT);
                if (!order.contains(normalized)) {
                    order.add(normalized);
                }
            }
        }
        if (!order.contains("mock")) {
            order.add("mock");
        }
        return String.join(",", order);
    }

    private Integer parseVersionNumber(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        try {
            String normalized = version.trim().toLowerCase();
            if (normalized.startsWith("v")) {
                normalized = normalized.substring(1);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AiOrchestrationResponse toResponse(AiOrchestrationRequest request,
                                               UUID requestId,
                                               AiProvider provider,
                                               AiProviderResponse providerResponse,
                                               AiPromptTemplateDefinition template,
                                               List<AiEvidenceReference> evidence,
                                               long started,
                                               boolean fallbackUsed,
                                               String errorMessage) {
        ParsedOutput parsed = parseProviderOutput(providerResponse, template);
        return toResponse(request, requestId, provider, providerResponse, template, evidence, started, fallbackUsed, errorMessage, parsed);
    }

    private AiOrchestrationResponse toResponse(AiOrchestrationRequest request,
                                               UUID requestId,
                                               AiProvider provider,
                                               AiProviderResponse providerResponse,
                                               AiPromptTemplateDefinition template,
                                               List<AiEvidenceReference> evidence,
                                               long started,
                                               boolean fallbackUsed,
                                               String errorMessage,
                                               ParsedOutput parsed) {
        List<String> suggestedActions = !parsed.suggestedActions().isEmpty()
                ? parsed.suggestedActions()
                : template.fallbackSuggestedActions();
        List<String> limitations = !parsed.limitations().isEmpty()
                ? parsed.limitations()
                : template.fallbackLimitations();
        if (fallbackUsed && limitations.stream().noneMatch(limit -> "Fallback provider was used. Please verify before acting.".equalsIgnoreCase(limit))) {
            limitations = new ArrayList<>(limitations);
            limitations.add("Fallback provider was used. Please verify before acting.");
        }
        BigDecimal confidence = parsed.confidence() != null
                ? parsed.confidence()
                : providerResponse.confidence() != null ? providerResponse.confidence() : BigDecimal.valueOf(0.78);
        String outputText = parsed.answer();
        if (outputText == null || outputText.isBlank()) {
            outputText = providerResponse.outputText();
        }
        return new AiOrchestrationResponse(
                requestId,
                requestId,
                request.productCode(),
                request.taskType(),
                provider.providerName(),
                providerResponse.model(),
                outputText,
                parsed.structuredJson(),
                confidence,
                evidence == null ? List.of() : List.copyOf(evidence),
                List.copyOf(suggestedActions),
                List.copyOf(limitations),
                providerResponse.tokenUsage(),
                System.currentTimeMillis() - started,
                fallbackUsed,
                errorMessage,
                providerResponse.finishReason(),
                parsed.normalizedFinishReason(),
                parsed.responseChars(),
                parsed.rawText(),
                parsed.parseStatus()
        );
    }

    private AiOrchestrationResponse fallbackResponse(AiOrchestrationRequest request,
                                                     UUID requestId,
                                                     AiPromptTemplateDefinition template,
                                                     String evidenceSummary,
                                                     long started,
                                                     String errorMessage) {
        List<String> suggestions = template.fallbackSuggestedActions();
        List<String> limitations = new ArrayList<>(template.fallbackLimitations());
        limitations.add("No autonomous action was taken.");
        String outputText = errorMessage == null || errorMessage.isBlank()
                ? "AI providers are temporarily unavailable. Please retry."
                : errorMessage;
        return new AiOrchestrationResponse(
                requestId,
                requestId,
                request.productCode(),
                request.taskType(),
                null,
                null,
                outputText,
                null,
                BigDecimal.valueOf(0.35),
                request.evidence() == null ? List.of() : List.copyOf(request.evidence()),
                List.copyOf(suggestions),
                List.copyOf(limitations),
                null,
                System.currentTimeMillis() - started,
                true,
                errorMessage,
                null,
                "FAILED",
                outputText == null ? 0 : outputText.length(),
                outputText,
                "FAILED"
        );
    }

    private boolean shouldAdvanceToNextProvider(AiOrchestrationRequest request, ParsedOutput parsed) {
        if (request == null || request.taskType() != AiTaskType.CLINICAL_REASONING || parsed == null) {
            return false;
        }
        String useCaseCode = request.useCaseCode() == null ? "" : request.useCaseCode().trim().toLowerCase();
        if (!useCaseCode.contains("repair")) {
            return false;
        }
        if (!"VALID".equalsIgnoreCase(parsed.parseStatus())) {
            return true;
        }
        return !hasCompleteClinicalReasoningCore(parsed.structuredJson());
    }

    private boolean hasCompleteClinicalReasoningCore(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            if (root == null || !root.isObject()) {
                return false;
            }
            String primaryDiagnosis = text(root.path("primaryDiagnosis"), "name");
            if (primaryDiagnosis == null || primaryDiagnosis.isBlank()) {
                return false;
            }
            String confidence = text(root, "confidence");
            if (confidence != null && !confidence.isBlank()) {
                return true;
            }
            JsonNode primaryConfidence = root.path("primaryDiagnosis").path("confidence");
            return !(primaryConfidence.isMissingNode() || primaryConfidence.isNull() || primaryConfidence.asText("").isBlank());
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> renderVariables(AiOrchestrationRequest request, AiPromptTemplateDefinition template) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("productCode", request.productCode() == null ? AiProductCode.GENERIC.name() : request.productCode().name());
        rendered.put("taskType", request.taskType() == null ? AiTaskType.GENERIC_COPILOT.name() : request.taskType().name());
        rendered.put("useCaseCode", safe(request.useCaseCode()));
        rendered.put("promptTemplateCode", safe(request.promptTemplateCode()));
        rendered.put("tenantId", request.tenantId() == null ? "-" : request.tenantId().toString());
        rendered.put("actorUserId", request.actorUserId() == null ? "-" : request.actorUserId().toString());
        rendered.put("correlationId", safe(request.correlationId()));
        rendered.put("inputVariablesJson", safeJson(request.inputVariables()));
        rendered.put("evidenceSummary", summarizeEvidence(request.evidence()));
        rendered.put("templateVersion", template.version());
        rendered.put("maxTokens", request.maxTokens());
        rendered.put("temperature", request.temperature());
        if (request.inputVariables() != null) {
            request.inputVariables().forEach((key, value) -> rendered.put("input." + key, value == null ? "" : value.toString()));
        }
        return rendered;
    }

    private String render(String template, Map<String, Object> variables, String evidenceSummary) {
        String rendered = template == null ? "" : template;
        if (evidenceSummary != null) {
            rendered = rendered.replace("{{evidenceSummary}}", evidenceSummary);
        }
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                rendered = rendered.replace("{{" + entry.getKey() + "}}", safe(entry.getValue()));
            }
        }
        return rendered;
    }

    private String summarizeEvidence(List<AiEvidenceReference> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "No evidence supplied.";
        }
        StringBuilder builder = new StringBuilder();
        for (AiEvidenceReference reference : evidence) {
            if (reference == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ");
            if (reference.entityType() != null) {
                builder.append(reference.entityType());
            }
            if (reference.entityId() != null) {
                builder.append("[").append(reference.entityId()).append("]");
            }
            if (reference.sourceReference() != null && !reference.sourceReference().isBlank()) {
                builder.append(" ref=").append(reference.sourceReference());
            }
            if (reference.textSnippet() != null && !reference.textSnippet().isBlank()) {
                builder.append(" :: ").append(trimTo(reference.textSnippet(), 220));
            }
        }
        return trimTo(builder.toString(), 1200);
    }

    private ParsedOutput parseProviderOutput(AiProviderResponse response, AiPromptTemplateDefinition template) {
        String raw = response == null ? null : firstNonBlank(response.rawText(), response.outputText());
        Integer responseChars = response == null ? null : response.responseChars();
        if (responseChars == null && raw != null) {
            responseChars = raw.length();
        }
        String normalizedFinishReason = AiFinishReasonNormalizer.normalize(response == null ? null : response.normalizedFinishReason());
        log.info("[NORMALIZED] provider={} model={} normalizedChars={} first300Chars=\"{}\" last300Chars=\"{}\" finishReason={} normalizedFinishReason={} parseStatus={}",
                response == null ? null : response.providerName(),
                response == null ? null : response.model(),
                raw == null ? 0 : raw.length(),
                previewStart(raw),
                previewEnd(raw),
                response == null ? null : response.finishReason(),
                normalizedFinishReason,
                response == null ? null : response.parseStatus());
        if ((raw == null || raw.isBlank())) {
            log.warn("AI provider returned empty content. provider={}", response == null ? "unknown" : response.providerName());
            String emptyMessage = normalizedFinishReason.equals("BLOCKED")
                    ? "AI response was blocked by safety filters. Please retry."
                    : normalizedFinishReason.equals("TRUNCATED")
                    ? "AI response was truncated. Please retry."
                    : "AI returned unstructured text. Please review carefully.";
            return invalidParsedOutput(emptyMessage, response == null ? null : response.confidence(), raw, normalizedFinishReason,
                    normalizedFinishReason.equals("BLOCKED") ? "BLOCKED" : normalizedFinishReason.equals("TRUNCATED") ? "TRUNCATED" : "FAILED");
        }

        boolean strictJson = requiresStrictJson(template);
        String jsonCandidate = extractJsonCandidate(raw);
        try {
            JsonNode root = objectMapper.readTree(jsonCandidate);
            if (!root.isObject()) {
                if (strictJson) {
                    return invalidParsedOutput("AI returned an invalid response. Please retry.",
                            response.confidence(), raw, normalizedFinishReason, parseStatusFromFinishReason(normalizedFinishReason, "FAILED"));
                }
                if (root.isArray()) {
                    JsonNode normalized = normalizeArrayOutput(root);
                    String answer = text(normalized, "summary");
                    if (answer == null) {
                        answer = text(normalized, "answer");
                    }
                    List<String> suggestions = new ArrayList<>(strings(normalized, "recommendedInvestigations"));
                    suggestions.addAll(strings(normalized, "followUpSuggestions"));
                    List<String> limitations = new ArrayList<>(strings(normalized, "safetyNotes"));
                    String safetyNote = text(normalized, "safetyNote");
                    if (safetyNote != null) {
                        limitations.add(safetyNote);
                    }
                    return new ParsedOutput(
                            answer == null ? "AI diagnosis suggestions generated. Please review." : answer,
                            normalized.toString(),
                            suggestions,
                            limitations,
                            response.confidence(),
                            raw,
                            responseChars,
                            "VALID",
                            normalizedFinishReason,
                            null
                    );
                }
                log.warn("AI provider returned non-object JSON. provider={}", response.providerName());
                return invalidParsedOutput("AI returned an invalid response. Please retry.", response.confidence(), raw, normalizedFinishReason,
                        parseStatusFromFinishReason(normalizedFinishReason, "FAILED"));
            }
            String answer = text(root, "answer");
            if (answer == null) {
                answer = text(root, "outputText");
            }
            if (answer == null) {
                answer = text(root, "summary");
            }
            BigDecimal confidence = decimal(root, "confidence");
            List<String> suggestions = new ArrayList<>(strings(root, "suggestedActions"));
            suggestions.addAll(strings(root, "recommendedInvestigations"));
            suggestions.addAll(strings(root, "followUpSuggestions"));
            List<String> limitations = new ArrayList<>(strings(root, "limitations"));
            limitations.addAll(strings(root, "safetyNotes"));
            boolean hasSuggestionsArray = root.path("suggestions").isArray() && root.path("suggestions").size() > 0;
            if (answer == null && hasSuggestionsArray) {
                answer = "AI suggestions generated. Please review.";
            }
            if (looksLikePromptLeak(raw)) {
                log.warn("AI provider output looked like prompt/template leakage. provider={}, rawPreview=\"{}\"",
                        response.providerName(),
                        trimTo(raw.replaceAll("[\\r\\n\\t]+", " "), 300));
                return invalidParsedOutput("AI returned an invalid response. Please retry.", response.confidence(), raw, normalizedFinishReason,
                        parseStatusFromFinishReason(normalizedFinishReason, "FAILED"));
            }
            return new ParsedOutput(answer == null ? raw : answer, root.toString(), suggestions, limitations,
                    confidence == null ? response.confidence() : confidence, raw, responseChars, "VALID", normalizedFinishReason, null);
        } catch (Exception ex) {
            if (looksLikePromptLeak(raw)) {
                log.warn("AI provider response matched prompt/template leakage. provider={}, rawPreview=\"{}\"",
                        response.providerName(),
                        trimTo(raw.replaceAll("[\\r\\n\\t]+", " "), 300));
                return invalidParsedOutput("AI returned an invalid response. Please retry.", response.confidence(), raw, normalizedFinishReason,
                        parseStatusFromFinishReason(normalizedFinishReason, "FAILED"));
            }
            if (AiFinishReasonNormalizer.isBlocked(normalizedFinishReason)) {
                return invalidParsedOutput("AI response was blocked by safety filters. Please retry.", response.confidence(), raw, normalizedFinishReason, "BLOCKED");
            }
            if (AiFinishReasonNormalizer.isTruncated(normalizedFinishReason)) {
                return invalidParsedOutput("AI response was truncated. Please retry.", response.confidence(), raw, normalizedFinishReason, "TRUNCATED");
            }
            log.warn("AI provider response parsing fallback used. provider={}, error={}, rawPreview=\"{}\"",
                    response.providerName(), safeMessage(ex), trimTo(raw.replaceAll("[\\r\\n\\t]+", " "), 300));
            if (strictJson) {
                return invalidParsedOutput("AI response could not be parsed. Please retry.", response.confidence(), raw, normalizedFinishReason, "FAILED");
            }
            String extractedAnswer = extractAnswerFromMalformedJson(raw);
            if (extractedAnswer != null) {
                return new ParsedOutput(
                        extractedAnswer,
                        fallbackStructuredJson(extractedAnswer),
                        List.of(),
                        List.of("AI returned partially structured output. Please verify."),
                        response.confidence(),
                        raw,
                        responseChars,
                        "VALID",
                        normalizedFinishReason,
                        null
                );
            }
            return new ParsedOutput(
                    raw,
                    fallbackStructuredJson(raw),
                    List.of(),
                    List.of("AI returned unstructured text. Please review carefully."),
                    response.confidence(),
                    raw,
                    responseChars,
                    "VALID",
                    normalizedFinishReason,
                    null
            );
        }
    }

    private String extractJsonCandidate(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart < 0) {
            fenceStart = trimmed.indexOf("```");
        }
        if (fenceStart >= 0) {
            int openEnd = trimmed.indexOf('\n', fenceStart);
            int fenceEnd = trimmed.indexOf("```", openEnd > -1 ? openEnd + 1 : fenceStart + 3);
            if (openEnd > -1 && fenceEnd > openEnd) {
                return trimmed.substring(openEnd + 1, fenceEnd).trim();
            }
        }
        return trimmed;
    }

    private boolean looksLikePromptLeak(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.toLowerCase().replaceAll("[\\r\\n\\t]+", " ");
        return normalized.contains("return only valid json")
                || normalized.contains("use exactly this shape")
                || normalized.contains("no markdown")
                || normalized.contains("no extra text")
                || normalized.contains("do not return a top-level array")
                || normalized.contains("\"medicine\": \"medicine name\"")
                || normalized.contains("\"diagnosis\": \"short name\"")
                || normalized.contains("one short sentence up to 140 chars");
    }

    private String extractAnswerFromMalformedJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = ANSWER_FIELD_PATTERN.matcher(raw);
        if (!matcher.find()) {
            int answerIndex = raw.indexOf("\"answer\"");
            if (answerIndex < 0) {
                return null;
            }
            int colonIndex = raw.indexOf(':', answerIndex);
            int openingQuoteIndex = colonIndex < 0 ? -1 : raw.indexOf('"', colonIndex + 1);
            if (openingQuoteIndex < 0) {
                return null;
            }
            int endIndex = raw.indexOf("\",", openingQuoteIndex + 1);
            if (endIndex < 0) {
                endIndex = raw.indexOf("\"}", openingQuoteIndex + 1);
            }
            String partial = endIndex > openingQuoteIndex
                    ? raw.substring(openingQuoteIndex + 1, endIndex)
                    : raw.substring(openingQuoteIndex + 1);
            partial = partial.replaceAll("[\\r\\n\\t]+", " ").trim();
            return partial.isBlank() ? null : partial;
        }
        String extracted = matcher.group(1)
                .replace("\\n", " ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return extracted.isBlank() ? null : extracted;
    }

    private JsonNode normalizeArrayOutput(JsonNode arrayNode) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (!item.isObject()) {
                continue;
            }
            String diagnosis = firstNonBlank(
                    text(item, "diagnosis"),
                    text(item, "condition"),
                    text(item, "name")
            );
            if (diagnosis == null) {
                diagnosis = "Condition";
            }
            String reason = firstNonBlank(
                    text(item, "reason"),
                    text(item, "reasoning")
            );
            List<String> redFlags = new ArrayList<>(strings(item, "redFlags"));
            if (redFlags.isEmpty()) {
                redFlags.addAll(strings(item, "redFlagExclusions"));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("diagnosis", diagnosis);
            row.put("reason", reason == null ? "" : reason);
            row.put("redFlags", redFlags);
            normalized.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", normalized.isEmpty() ? "AI returned structured array output without usable diagnosis items." : "AI diagnosis suggestions generated. Please review.");
        payload.put("suggestions", normalized);
        payload.put("recommendedInvestigations", List.of());
        payload.put("followUpSuggestions", List.of());
        payload.put("safetyNote", "AI suggestions are assistive only and must be reviewed.");
        payload.put("safetyNotes", List.of("AI suggestions are assistive only and must be reviewed."));
        return objectMapper.valueToTree(payload);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String fallbackStructuredJson(String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary == null ? "" : summary.trim());
        payload.put("suggestions", List.of());
        payload.put("safetyNote", "AI suggestions are assistive only and must be reviewed.");
        payload.put("possibleDiagnosisCategories", List.of());
        payload.put("recommendedInvestigations", List.of());
        payload.put("followUpSuggestions", List.of());
        payload.put("safetyNotes", List.of("AI returned unstructured text. Please review carefully."));
        return safeJson(payload);
    }

    private String invalidStructuredJson(String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary == null || summary.isBlank() ? "AI returned an invalid response. Please retry." : summary.trim());
        payload.put("suggestions", List.of());
        payload.put("safetyNote", "AI suggestions are assistive only and must be reviewed.");
        payload.put("possibleDiagnosisCategories", List.of());
        payload.put("recommendedInvestigations", List.of());
        payload.put("followUpSuggestions", List.of());
        payload.put("safetyNotes", List.of(payload.get("summary"), "AI suggestions are assistive only and must be reviewed."));
        return safeJson(payload);
    }

    private ParsedOutput invalidParsedOutput(String message, BigDecimal confidence, String rawText,
                                             String normalizedFinishReason, String parseStatus) {
        return new ParsedOutput(
                message,
                invalidStructuredJson(message),
                List.of(),
                List.of(message, "AI suggestions are assistive only and must be reviewed."),
                confidence,
                rawText,
                rawText == null ? 0 : rawText.length(),
                parseStatus,
                normalizedFinishReason,
                message
        );
    }

    private boolean looksLikeJson(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean requiresStrictJson(AiPromptTemplateDefinition template) {
        String text = ((template == null ? null : template.systemPrompt()) == null ? "" : template.systemPrompt())
                + "\n"
                + ((template == null ? null : template.userPromptTemplate()) == null ? "" : template.userPromptTemplate());
        String normalized = text.toLowerCase();
        return normalized.contains("return only valid json")
                || normalized.contains("return only strict json")
                || normalized.contains("strict json")
                || normalized.contains("no prose outside json")
                || normalized.contains("no markdown")
                || normalized.contains("use exactly this shape")
                || normalized.contains("return only json");
    }

    private String parseStatusFromFinishReason(String normalizedFinishReason, String defaultStatus) {
        if (AiFinishReasonNormalizer.isTruncated(normalizedFinishReason)) {
            return "TRUNCATED";
        }
        if (AiFinishReasonNormalizer.isBlocked(normalizedFinishReason)) {
            return "BLOCKED";
        }
        if (AiFinishReasonNormalizer.isFailed(normalizedFinishReason)) {
            return "FAILED";
        }
        return defaultStatus == null ? "FAILED" : defaultStatus;
    }

    private String requestHash(AiOrchestrationRequest request, AiPromptTemplateDefinition template, String evidenceSummary) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = safe(request.productCode()) + "|" + safe(request.tenantId()) + "|" + safe(request.taskType())
                    + "|" + safe(template.templateCode()) + "|" + safe(template.version()) + "|" + safe(request.useCaseCode())
                    + "|" + safeJson(request.inputVariables()) + "|" + safe(evidenceSummary);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            return null;
        }
    }

    private String inputSummary(AiOrchestrationRequest request, AiPromptTemplateDefinition template,
                                Map<String, Object> renderedVariables, String evidenceSummary) {
        return "product=%s task=%s template=%s version=%s evidenceCount=%d variables=%s evidence=%s"
                .formatted(
                        safe(request.productCode()),
                        safe(request.taskType()),
                        safe(template.templateCode()),
                        safe(template.version()),
                        request.evidence() == null ? 0 : request.evidence().size(),
                        safeJson(renderedVariables),
                        trimTo(evidenceSummary, 1000)
                );
    }

    private String outputSummary(String outputText) {
        return trimTo(outputText, 1000);
    }

    private String safeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String safe(Object value) {
        return value == null ? "-" : value.toString();
    }

    private String trimTo(String value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText(null) == null || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> strings(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        value.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                results.add(text.trim());
            }
        });
        return results;
    }

    private Long tokenUsage(AiTokenUsage tokenUsage, boolean prompt) {
        if (tokenUsage == null) {
            return null;
        }
        return prompt ? tokenUsage.promptTokens() : tokenUsage.completionTokens();
    }

    private String safeMessage(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex == null ? "AI provider unavailable" : ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }

    private String previewStart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private String previewEnd(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(normalized.length() - 300);
    }

    private record ParsedOutput(String answer, String structuredJson, List<String> suggestedActions,
                                List<String> limitations, BigDecimal confidence, String rawText,
                                Integer responseChars, String parseStatus, String normalizedFinishReason,
                                String errorMessage) {
        private static ParsedOutput empty() {
            return new ParsedOutput(null, null, List.of(), List.of(), null, null, 0, "UNKNOWN", "UNKNOWN", null);
        }
    }
}
