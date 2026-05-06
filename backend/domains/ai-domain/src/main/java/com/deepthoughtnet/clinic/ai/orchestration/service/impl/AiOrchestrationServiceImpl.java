package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiPromptTemplateRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOrchestrationServiceImpl implements AiOrchestrationService {
    private final AiPromptTemplateRegistryService templateRegistry;
    private final AiProviderRouter providerRouter;
    private final AiRequestAuditService auditService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationServiceImpl(AiPromptTemplateRegistryService templateRegistry,
                                      AiProviderRouter providerRouter,
                                      AiRequestAuditService auditService,
                                      ObjectMapper objectMapper) {
        this.templateRegistry = templateRegistry;
        this.providerRouter = providerRouter;
        this.auditService = auditService;
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
            try {
                providerResponse = candidate.complete(providerRequest);
                provider = candidate;
                fallbackUsed = i > 0;
                response = toResponse(request, requestId, provider, providerResponse, template, request.evidence(),
                        started, fallbackUsed, fallbackUsed ? "Fallback provider was used. Please verify before acting." : null);
                break;
            } catch (RuntimeException ex) {
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
        return response;
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
            return ParsedOutput.empty();
        }
        String raw = response.outputText().trim();
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject()) {
                return new ParsedOutput(raw, null, List.of(), List.of(), response.confidence(), raw);
            }
            String answer = text(root, "answer");
            if (answer == null) {
                answer = text(root, "outputText");
            }
            if (answer == null) {
                answer = text(root, "summary");
            }
            BigDecimal confidence = decimal(root, "confidence");
            List<String> suggestions = strings(root, "suggestedActions");
            List<String> limitations = strings(root, "limitations");
            return new ParsedOutput(answer == null ? raw : answer, raw, suggestions, limitations,
                    confidence == null ? response.confidence() : confidence, raw);
        } catch (Exception ex) {
            return new ParsedOutput(raw, null, List.of(), List.of(), response.confidence(), null);
        }
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
