package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiGuardrailService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiInvocationLogService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOrchestrationServiceImpl implements AiOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationServiceImpl.class);
    private final AiPromptTemplateRegistryService templateRegistry;
    private final AiProviderRouter providerRouter;
    private final AiRequestAuditService auditService;
    private final AiGuardrailService guardrailService;
    private final AiInvocationLogService invocationLogService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationServiceImpl(AiPromptTemplateRegistryService templateRegistry,
                                      AiProviderRouter providerRouter,
                                      AiRequestAuditService auditService,
                                      AiGuardrailService guardrailService,
                                      AiInvocationLogService invocationLogService,
                                      ObjectMapper objectMapper) {
        this.templateRegistry = templateRegistry;
        this.providerRouter = providerRouter;
        this.auditService = auditService;
        this.guardrailService = guardrailService;
        this.invocationLogService = invocationLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiOrchestrationResponse complete(AiOrchestrationRequest request) {
        long started = System.currentTimeMillis();
        UUID requestId = UUID.randomUUID();
        AiPromptTemplateDefinition template = templateRegistry.resolve(request);
        Map<String, Object> renderedVariables = renderVariables(request, template);
        String evidenceSummary = summarizeEvidence(request.evidence());
        String userPrompt = render(template.userPromptTemplate(), renderedVariables, evidenceSummary);
        guardrailService.validatePreExecution(request.tenantId(), userPrompt, request, null);

        AiProvider provider = null;
        AiProviderResponse providerResponse = null;
        RuntimeException firstFailure = null;
        RuntimeException lastFailure = null;
        boolean fallbackUsed = false;
        AiOrchestrationResponse response = null;
        List<AiProvider> candidates = providerRouter.resolveCandidates(request.taskType());
        AiProviderRequest providerRequest = new AiProviderRequest(
                request,
                template.version(),
                template.systemPrompt(),
                userPrompt,
                renderedVariables,
                request.evidence(),
                requestId
        );
        for (int i = 0; i < candidates.size(); i++) {
            AiProvider candidate = candidates.get(i);
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
                log.info("AI provider completed. requestId={}, provider={}, latencyMs={}, responseChars={}",
                        requestId,
                        candidate.providerName(),
                        System.currentTimeMillis() - providerStarted,
                        providerResponse == null || providerResponse.outputText() == null ? 0 : providerResponse.outputText().length());
                response = toResponse(request, requestId, provider, providerResponse, template, request.evidence(),
                        started, fallbackUsed, fallbackUsed ? "Fallback provider was used. Please verify before acting." : null);
                break;
            } catch (RuntimeException ex) {
                log.warn("AI provider failed. requestId={}, provider={}, latencyMs={}, error={}",
                        requestId,
                        candidate.providerName(),
                        System.currentTimeMillis() - providerStarted,
                        safeMessage(ex));
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                lastFailure = ex;
            }
        }

        if (response == null) {
            fallbackUsed = true;
            String safeError = firstFailure == null ? null : safeMessage(firstFailure);
            String errorMessage = safeError == null ? "AI provider unavailable" : safeError;
            response = fallbackResponse(request, requestId, template, evidenceSummary, started, errorMessage);
            lastFailure = firstFailure == null ? lastFailure : firstFailure;
        }

        auditService.record(new AiRequestAuditCommand(
                requestId,
                request.productCode() == null ? AiProductCode.GENERIC.name() : request.productCode().name(),
                request.tenantId(),
                request.actorUserId(),
                request.useCaseCode(),
                request.taskType() == null ? AiTaskType.GENERIC_COPILOT.name() : request.taskType().name(),
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
                request.correlationId()
        ));
        invocationLogService.record(new AiInvocationLogService.InvocationLogCommand(
                request.tenantId(),
                requestId,
                request.correlationId(),
                request.productCode() == null ? AiProductCode.GENERIC.name() : request.productCode().name(),
                request.useCaseCode(),
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
                request.actorUserId()
        ));
        return response;
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
        ParsedOutput parsed = parseProviderOutput(providerResponse);
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
                errorMessage
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
        String outputText = template.fallbackSummary();
        if (evidenceSummary != null && !evidenceSummary.isBlank()) {
            outputText = outputText + " Context: " + evidenceSummary;
        }
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
                errorMessage
        );
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

    private ParsedOutput parseProviderOutput(AiProviderResponse response) {
        if (response == null || response.outputText() == null || response.outputText().isBlank()) {
            log.warn("AI provider returned empty content. provider={}", response == null ? "unknown" : response.providerName());
            String structured = fallbackStructuredJson("");
            return new ParsedOutput("", structured, List.of(), List.of("AI returned unstructured text. Please review carefully."), response == null ? null : response.confidence(), "");
        }
        String raw = response.outputText().trim();
        String jsonCandidate = extractJsonCandidate(raw);
        try {
            JsonNode root = objectMapper.readTree(jsonCandidate);
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
                        raw
                );
            }
            if (!root.isObject()) {
                log.warn("AI provider returned non-object JSON. provider={}", response.providerName());
                String structured = fallbackStructuredJson(raw);
                return new ParsedOutput(raw, structured, List.of(), List.of("AI returned unstructured text. Please review carefully."), response.confidence(), raw);
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
            return new ParsedOutput(answer == null ? raw : answer, root.toString(), suggestions, limitations,
                    confidence == null ? response.confidence() : confidence, raw);
        } catch (Exception ex) {
            boolean likelyTruncatedJson = looksLikeJson(raw);
            log.warn("AI provider response parsing fallback used. provider={}, error={}, rawPreview=\"{}\"",
                    response.providerName(), safeMessage(ex), trimTo(raw.replaceAll("[\\r\\n\\t]+", " "), 300));
            if (likelyTruncatedJson) {
                String structured = incompleteStructuredJson();
                return new ParsedOutput(
                        "AI response was incomplete. Please retry.",
                        structured,
                        List.of(),
                        List.of("AI suggestions are assistive only and must be reviewed."),
                        response.confidence(),
                        "AI response was incomplete. Please retry."
                );
            }
            String structured = fallbackStructuredJson(raw);
            return new ParsedOutput(raw, structured, List.of(), List.of("AI returned unstructured text. Please review carefully."), response.confidence(), raw);
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

    private String incompleteStructuredJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "AI response was incomplete. Please retry.");
        payload.put("suggestions", List.of());
        payload.put("safetyNote", "AI suggestions are assistive only and must be reviewed.");
        payload.put("possibleDiagnosisCategories", List.of());
        payload.put("recommendedInvestigations", List.of());
        payload.put("followUpSuggestions", List.of());
        payload.put("safetyNotes", List.of("AI response was incomplete. Please retry.", "AI suggestions are assistive only and must be reviewed."));
        return safeJson(payload);
    }

    private boolean looksLikeJson(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
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

    private record ParsedOutput(String answer, String structuredJson, List<String> suggestedActions,
                                List<String> limitations, BigDecimal confidence, String rawText) {
        private static ParsedOutput empty() {
            return new ParsedOutput(null, null, List.of(), List.of(), null, null);
        }
    }
}
