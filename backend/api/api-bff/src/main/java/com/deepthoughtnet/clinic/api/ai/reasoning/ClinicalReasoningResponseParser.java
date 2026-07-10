package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalSafetyNote;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.DiagnosisCandidate;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.EvidenceItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.MissingInformationItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.RedFlagItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.RecommendedTestItem;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClinicalReasoningResponseParser {
    private static final Logger log = LoggerFactory.getLogger(ClinicalReasoningResponseParser.class);
    private final ObjectMapper objectMapper;

    public ClinicalReasoningResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ClinicalReasoningResult parse(UUID consultationId,
                                         UUID patientId,
                                         ClinicalContextResponse context,
                                         AiDraftResponse response,
                                         boolean repairAttempt,
                                         String requestId,
                                         String correlationId,
                                         Long latencyMs) {
        String finishReason = response == null ? null : response.finishReason();
        String normalizedFinishReason = response == null ? null : response.normalizedFinishReason();
        String parseStatus = response == null ? null : response.parseStatus();
        int rawChars = rawChars(response);
        String parserInput = firstNonBlank(response == null ? null : response.rawText(), response == null ? null : response.draft(), response == null ? null : response.message());
        log.info("[PARSER-INPUT] chars={} first300Chars=\"{}\" last300Chars=\"{}\" finishReason={} normalizedFinishReason={} parseStatus={}",
                parserInput == null ? 0 : parserInput.length(),
                previewStart(parserInput),
                previewEnd(parserInput),
                finishReason,
                normalizedFinishReason,
                parseStatus);
        if (AiFinishReasonNormalizer.isTruncated(normalizedFinishReason) || "TRUNCATED".equalsIgnoreCase(parseStatus)) {
            return empty(consultationId, patientId, context, response, repairAttempt, requestId, correlationId, latencyMs,
                    "TRUNCATED", rawChars, "AI reasoning response was truncated. Please retry.", normalizedFinishReason);
        }
        if (AiFinishReasonNormalizer.isBlocked(normalizedFinishReason) || "BLOCKED".equalsIgnoreCase(parseStatus)) {
            return empty(consultationId, patientId, context, response, repairAttempt, requestId, correlationId, latencyMs,
                    "BLOCKED", rawChars, "AI reasoning response was blocked by safety filters. Please retry.", normalizedFinishReason);
        }
        if ("FAILED".equalsIgnoreCase(parseStatus)) {
            return empty(consultationId, patientId, context, response, repairAttempt, requestId, correlationId, latencyMs,
                    "FAILED", rawChars, response == null ? "AI reasoning response could not be parsed. Please retry." : response.message(), normalizedFinishReason);
        }
        JsonNode root = resolveRootNode(response);
        if (root == null || !root.isObject()) {
            return empty(consultationId, patientId, context, response, repairAttempt, requestId, correlationId, latencyMs,
                    "FAILED", rawChars, "AI reasoning response could not be parsed. Please retry.", normalizedFinishReason);
        }
        ClinicalReasoningResult mapped = mapResult(root, consultationId, patientId, context, response, "VALID", requestId, correlationId, latencyMs,
                response != null && response.fallbackUsed(), repairAttempt, rawChars, null, normalizedFinishReason);
        if (!hasReasoningContent(mapped)) {
            return empty(consultationId, patientId, context, response, repairAttempt, requestId, correlationId, latencyMs,
                    "FAILED", rawChars, "AI reasoning response did not contain usable clinical reasoning.", normalizedFinishReason);
        }
        return mapped;
    }

    private ClinicalReasoningResult mapResult(JsonNode root,
                                              UUID consultationId,
                                              UUID patientId,
                                              ClinicalContextResponse context,
                                              AiDraftResponse response,
                                              String parseStatus,
                                              String requestId,
                                              String correlationId,
                                              Long latencyMs,
                                              boolean fallbackUsed,
                                              boolean retryUsed,
                                              Integer rawChars,
                                              String errorMessage,
                                              String normalizedFinishReason) {
        DiagnosisCandidate primaryDiagnosis = parseDiagnosisCandidate(root.path("primaryDiagnosis"));
        List<DiagnosisCandidate> differentialDiagnoses = parseDiagnosisCandidates(root.path("differentialDiagnoses"));
        List<EvidenceItem> supportingEvidence = parseEvidenceItems(root.path("supportingEvidence"));
        List<EvidenceItem> contradictingEvidence = parseEvidenceItems(root.path("contradictingEvidence"));
        List<MissingInformationItem> missingInformation = parseMissingInformationItems(root.path("missingInformation"));
        List<RedFlagItem> redFlags = parseRedFlags(root.path("redFlags"));
        List<RecommendedTestItem> recommendedTests = parseRecommendedTests(root.path("recommendedTests"));
        List<ClinicalSafetyNote> safetyNotes = parseSafetyNotes(root.path("safetyNotes"));
        List<String> followUpAdvice = parseStrings(root.path("followUpAdvice"));
        String reasoningSummary = text(root, "reasoningSummary", "summary", "answer");
        String patientExplanation = text(root, "patientExplanation", "explanation");
        ClinicalReasoningResult.SourceContextSummary sourceContextSummary = parseSourceContextSummary(root.path("sourceContextSummary"), context, consultationId);
        ReasoningMetadata metadata = new ReasoningMetadata(
                "clinic.clinical.reasoning.engine.v1",
                text(root.path("metadata"), "promptVersion"),
                text(root.path("metadata"), "contextVersion"),
                "v1",
                firstNonBlank(text(root.path("metadata"), "provider"), response.provider()),
                firstNonBlank(text(root.path("metadata"), "model"), response.model()),
                parseTokens(root.path("metadata").path("tokens")),
                parseStatus,
                requestId,
                correlationId,
                latencyMs,
                fallbackUsed,
                retryUsed,
                response == null ? null : response.finishReason(),
                normalizedFinishReason,
                response == null ? null : response.responseChars(),
                response == null ? null : response.rawText(),
                rawChars,
                errorMessage,
                null
        );
        String confidence = resolveConfidence(root, primaryDiagnosis);
        return new ClinicalReasoningResult(
                consultationId,
                patientId,
                OffsetDateTime.now(),
                firstNonBlank(text(root.path("metadata"), "provider"), response.provider()),
                firstNonBlank(text(root.path("metadata"), "model"), response.model()),
                confidence,
                primaryDiagnosis,
                differentialDiagnoses,
                supportingEvidence,
                contradictingEvidence,
                missingInformation,
                redFlags,
                recommendedTests,
                reasoningSummary,
                safetyNotes,
                followUpAdvice,
                patientExplanation,
                sourceContextSummary,
                metadata
        );
    }

    private ClinicalReasoningResult empty(UUID consultationId,
                                          UUID patientId,
                                          ClinicalContextResponse context,
                                          AiDraftResponse response,
                                          boolean repairAttempt,
                                          String requestId,
                                          String correlationId,
                                          Long latencyMs,
                                          String parseStatus,
                                          Integer rawChars,
                                          String errorMessage,
                                          String normalizedFinishReason) {
        ClinicalReasoningResult.SourceContextSummary sourceContextSummary = parseSourceContextSummary(null, context, consultationId);
        ReasoningMetadata metadata = new ReasoningMetadata(
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.CONTEXT_VERSION,
                ClinicalReasoningPromptBuilder.SCHEMA_VERSION,
                response == null ? null : response.provider(),
                response == null ? null : response.model(),
                Map.of(),
                parseStatus,
                requestId,
                correlationId,
                latencyMs,
                response != null && response.fallbackUsed(),
                repairAttempt,
                response == null ? null : response.finishReason(),
                normalizedFinishReason,
                response == null ? null : response.responseChars(),
                response == null ? null : response.rawText(),
                rawChars,
                errorMessage,
                null
        );
        return new ClinicalReasoningResult(
                consultationId,
                patientId,
                OffsetDateTime.now(),
                response == null ? null : response.provider(),
                response == null ? null : response.model(),
                "UNKNOWN",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                sourceContextSummary,
                metadata
        );
    }

    private int rawChars(AiDraftResponse response) {
        if (response == null) {
            return 0;
        }
        if (response.responseChars() != null) {
            return response.responseChars();
        }
        String raw = firstNonBlank(response.rawText(), response.draft(), response.message());
        if (raw != null) {
            return raw.length();
        }
        if (response.structuredData() != null && !response.structuredData().isEmpty()) {
            return response.structuredData().toString().length();
        }
        return 0;
    }

    private boolean hasReasoningContent(ClinicalReasoningResult result) {
        return result != null && (
                (result.primaryDiagnosis() != null && hasText(result.primaryDiagnosis().name()))
                        || !result.differentialDiagnoses().isEmpty()
                        || hasText(result.reasoningSummary())
                        || !result.redFlags().isEmpty()
                        || !result.recommendedTests().isEmpty()
        );
    }

    private JsonNode resolveRootNode(AiDraftResponse response) {
        if (response == null) {
            return null;
        }
        JsonNode structuredNode = structuredNode(response);
        String raw = firstNonBlank(response.rawText(), response.draft(), response.message());
        if (raw == null && structuredNode != null && structuredNode.has("raw")) {
            raw = structuredNode.path("raw").asText(null);
        }
        if (raw == null || raw.isBlank()) {
            return structuredNode;
        }
        String candidate = extractJsonCandidate(raw);
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException ex) {
            if (structuredNode != null && structuredNode.isObject() && !isRawOnly(structuredNode)) {
                return structuredNode;
            }
            return structuredNode;
        }
    }

    private JsonNode structuredNode(AiDraftResponse response) {
        return response.structuredData() == null || response.structuredData().isEmpty()
                ? null
                : objectMapper.valueToTree(response.structuredData());
    }

    private boolean isRawOnly(JsonNode node) {
        return node.size() == 1 && node.has("raw");
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

    private String extractJsonCandidate(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int fenceEnd = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && fenceEnd > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, fenceEnd).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim();
        }
        return trimmed;
    }

    private DiagnosisCandidate parseDiagnosisCandidate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new DiagnosisCandidate(
                text(node, "name", "diagnosis", "title"),
                decimal(node, "confidence"),
                text(node, "status"),
                text(node, "whyConsidered", "reason", "reasoning"),
                text(node, "whyLessLikely"),
                parseEvidenceItems(node.path("supportingEvidence")),
                parseEvidenceItems(node.path("contradictingEvidence")),
                parseMissingInformationItems(node.path("missingInformation")),
                parseRecommendedTests(node.path("recommendedTests")),
                parseRedFlags(node.path("redFlags"))
        );
    }

    private List<DiagnosisCandidate> parseDiagnosisCandidates(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DiagnosisCandidate> candidates = new ArrayList<>();
        for (JsonNode element : node) {
            DiagnosisCandidate candidate = parseDiagnosisCandidate(element);
            if (candidate != null && hasText(candidate.name())) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private List<EvidenceItem> parseEvidenceItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<EvidenceItem> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                String text = element.asText().trim();
                if (!text.isBlank()) {
                    items.add(new EvidenceItem(text, null, null, null, null, null, null, null));
                }
                continue;
            }
            items.add(new EvidenceItem(
                    text(element, "text", "value", "description"),
                    text(element, "source"),
                    text(element, "observationDate", "date", "observedAt"),
                    decimal(element, "confidence"),
                    text(element, "type", "kind"),
                    text(element, "sourceType"),
                    text(element, "sourceTitle"),
                    text(element, "verificationStatus")
            ));
        }
        return items;
    }

    private List<MissingInformationItem> parseMissingInformationItems(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<MissingInformationItem> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                String text = element.asText().trim();
                if (!text.isBlank()) {
                    items.add(new MissingInformationItem(text, null, null, null));
                }
                continue;
            }
            items.add(new MissingInformationItem(
                    text(element, "name", "title", "text"),
                    text(element, "whyItMatters", "reason"),
                    text(element, "requestedAction", "action"),
                    decimal(element, "confidence")
            ));
        }
        return items;
    }

    private List<RedFlagItem> parseRedFlags(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RedFlagItem> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                String text = element.asText().trim();
                if (!text.isBlank()) {
                    items.add(new RedFlagItem(text, null, null, null, null, null, null, null, null, null));
                }
                continue;
            }
            items.add(new RedFlagItem(
                    text(element, "name", "title", "text"),
                    text(element, "reason", "why"),
                    text(element, "severity"),
                    text(element, "action", "recommendedAction"),
                    decimal(element, "confidence"),
                    text(element, "source"),
                    text(element, "observationDate", "date", "observedAt"),
                    text(element, "sourceType"),
                    text(element, "sourceTitle"),
                    text(element, "verificationStatus")
            ));
        }
        return items;
    }

    private List<RecommendedTestItem> parseRecommendedTests(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RecommendedTestItem> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                String text = element.asText().trim();
                if (!text.isBlank()) {
                    items.add(new RecommendedTestItem(text, null, null, null, null, null, null, null, null, null, null, null, null));
                }
                continue;
            }
            items.add(new RecommendedTestItem(
                    text(element, "name", "title", "text"),
                    text(element, "reason", "why"),
                    text(element, "priority"),
                    text(element, "timing"),
                    decimal(element, "confidence"),
                    text(element, "source"),
                    text(element, "observationDate", "date", "observedAt"),
                    text(element, "sourceType"),
                    text(element, "sourceTitle"),
                    text(element, "verificationStatus"),
                    bool(element, "alreadyAvailable"),
                    bool(element, "pendingOrderExists"),
                    text(element, "actionType")
            ));
        }
        return items;
    }

    private List<ClinicalSafetyNote> parseSafetyNotes(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ClinicalSafetyNote> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                String text = element.asText().trim();
                if (!text.isBlank()) {
                    items.add(new ClinicalSafetyNote(text, null, null, null, null, null, null, null));
                }
                continue;
            }
            items.add(new ClinicalSafetyNote(
                    text(element, "message", "text", "note"),
                    text(element, "severity"),
                    text(element, "rationale", "reason"),
                    text(element, "action"),
                    text(element, "sourceType"),
                    text(element, "sourceTitle"),
                    text(element, "verificationStatus"),
                    text(element, "actionType")
            ));
        }
        return items;
    }

    private Boolean bool(JsonNode node, String field) {
        if (node == null || field == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        if (node.get(field).isBoolean()) {
            return node.get(field).asBoolean();
        }
        String text = node.get(field).asText();
        if (text == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "1".equals(text.trim())) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text) || "0".equals(text.trim())) {
            return Boolean.FALSE;
        }
        return null;
    }

    private ClinicalReasoningResult.SourceContextSummary parseSourceContextSummary(JsonNode node,
                                                                                   ClinicalContextResponse context,
                                                                                   UUID consultationId) {
        if (node != null && node.isObject()) {
            return new ClinicalReasoningResult.SourceContextSummary(
                    text(node, "chiefComplaint"),
                    parseStrings(node.path("symptoms")),
                    text(node, "vitals"),
                    parseStrings(node.path("knownConditions")),
                    parseStrings(node.path("recentReports")),
                    parseStrings(node.path("currentMedicines"))
            );
        }
        List<String> knownConditions = context == null || context.longitudinalMemory() == null
                ? List.of()
                : context.longitudinalMemory().knownConditions().stream().map(value -> value.label() == null || value.label().isBlank() ? value.valueText() : value.label()).filter(this::hasText).toList();
        List<String> currentMedicines = context == null || context.longitudinalMemory() == null
                ? List.of()
                : context.longitudinalMemory().longTermMedications().stream().map(value -> value.label() == null || value.label().isBlank() ? value.valueText() : value.label()).filter(this::hasText).toList();
        List<String> recentReports = context == null || context.documentIntelligence() == null
                ? List.of()
                : context.documentIntelligence().recentReports().stream().filter(this::hasText).toList();
        String chiefComplaint = context == null || context.intakeSummary() == null ? null : context.intakeSummary().chiefComplaint();
        List<String> symptoms = context == null || context.intakeSummary() == null || context.intakeSummary().chiefComplaint() == null
                ? List.of()
                : List.of(context.intakeSummary().chiefComplaint());
        String vitals = context == null || context.intakeSummary() == null || context.intakeSummary().latestVitals() == null
                ? null
                : buildVitals(context.intakeSummary().latestVitals());
        return new ClinicalReasoningResult.SourceContextSummary(chiefComplaint, symptoms, vitals, knownConditions, recentReports, currentMedicines);
    }

    private String buildVitals(ClinicalContextResponse.VitalsSnapshot vitals) {
        List<String> parts = new ArrayList<>();
        if (vitals.bloodPressureSystolic() != null && vitals.bloodPressureDiastolic() != null) {
            parts.add("BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic());
        }
        if (vitals.pulseRate() != null) {
            parts.add("Pulse " + vitals.pulseRate());
        }
        if (vitals.temperature() != null) {
            parts.add("Temp " + vitals.temperature() + (vitals.temperatureUnit() == null || vitals.temperatureUnit().isBlank() ? "" : " " + vitals.temperatureUnit()));
        }
        if (vitals.spo2() != null) {
            parts.add("SpO2 " + vitals.spo2());
        }
        if (vitals.randomBloodSugar() != null) {
            parts.add("RBS " + vitals.randomBloodSugar());
        }
        return String.join(", ", parts);
    }

    private List<String> parseStrings(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode element : node) {
                if (element == null || element.isNull()) {
                    continue;
                }
                String value = element.asText().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return values;
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (value.isBlank()) {
                return List.of();
            }
            return List.of(value);
        }
        return List.of();
    }

    private Map<String, Object> parseTokens(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private String text(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull()) {
                String value = child.asText().trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isNumber()) {
            return child.decimalValue();
        }
        String value = child.asText().trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeConfidence(String confidence) {
        if (confidence == null || confidence.isBlank()) {
            return "UNKNOWN";
        }
        String value = confidence.trim().toUpperCase();
        if (value.matches("\\d+(\\.\\d+)?")) {
            try {
                BigDecimal numeric = new BigDecimal(value);
                if (numeric.compareTo(new BigDecimal("0.9")) >= 0) {
                    return "VERY_HIGH";
                }
                if (numeric.compareTo(new BigDecimal("0.75")) >= 0) {
                    return "HIGH";
                }
                if (numeric.compareTo(new BigDecimal("0.6")) >= 0) {
                    return "MEDIUM";
                }
                return "LOW";
            } catch (NumberFormatException ex) {
                return "UNKNOWN";
            }
        }
        return value;
    }

    private String resolveConfidence(JsonNode root, DiagnosisCandidate primaryDiagnosis) {
        String confidence = normalizeConfidence(text(root, "confidence"));
        if (!"UNKNOWN".equalsIgnoreCase(confidence)) {
            return confidence;
        }
        if (primaryDiagnosis == null || primaryDiagnosis.confidence() == null) {
            return confidence;
        }
        BigDecimal numeric = primaryDiagnosis.confidence();
        if (numeric.compareTo(new BigDecimal("0.8")) >= 0) {
            return "HIGH";
        }
        if (numeric.compareTo(new BigDecimal("0.6")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
