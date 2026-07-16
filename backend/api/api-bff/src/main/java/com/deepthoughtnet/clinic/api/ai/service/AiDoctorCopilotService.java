package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiDoctorCopilotService {
    private static final Logger log = LoggerFactory.getLogger(AiDoctorCopilotService.class);
    private static final String SAFETY_NOTICE = "This is an AI-generated draft. Doctor must verify before use.";
    private static final String CONSULTATION_ASK_TEMPLATE_CODE = "clinic.consultation.ask.v1";
    private static final String CONSULTATION_ASK_USE_CASE = "consultation.ask";
    private static final Integer CONSULTATION_ASK_MAX_OUTPUT_TOKENS = 1024;

    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private boolean soapTraceEnabled;
    private boolean soapTraceRawResponseEnabled;

    public AiDoctorCopilotService(AiOrchestrationService aiOrchestrationService,
                                  ObjectMapper objectMapper,
                                  @Value("${clinic.ai.enabled:false}") boolean enabled) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Value("${JEEVANAM_AI_SOAP_TRACE_ENABLED:false}")
    void setSoapTraceEnabled(boolean soapTraceEnabled) {
        this.soapTraceEnabled = soapTraceEnabled;
    }

    @Value("${JEEVANAM_AI_SOAP_TRACE_RAW_RESPONSE_ENABLED:false}")
    void setSoapTraceRawResponseEnabled(boolean soapTraceRawResponseEnabled) {
        this.soapTraceRawResponseEnabled = soapTraceRawResponseEnabled;
    }

    public AiDraftResponse draft(AiTaskType taskType,
                                 String promptTemplateCode,
                                 String useCaseCode,
                                 Map<String, Object> input,
                                 List<AiEvidenceReference> evidence) {
        if (!enabled) {
            return disabledResponse();
        }

        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorUserId = RequestContextHolder.require().appUserId();
        String correlationId = RequestContextHolder.require().correlationId();
        String consultationId = safeId(input == null ? null : input.get("consultationId"));
        String patientId = safeId(input == null ? null : input.get("patientId"));
        Integer maxTokens = isConsultationAsk(promptTemplateCode, useCaseCode) ? CONSULTATION_ASK_MAX_OUTPUT_TOKENS : null;

        AiOrchestrationResponse response = aiOrchestrationService.complete(new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                actorUserId,
                taskType,
                promptTemplateCode,
                input,
                evidence,
                maxTokens,
                0.1d,
                correlationId,
                useCaseCode
        ));

        List<String> warnings = new java.util.ArrayList<>();
        warnings.add(SAFETY_NOTICE);
        if (response.limitations() != null) {
            warnings.addAll(response.limitations());
        }

        Map<String, Object> structured = taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING
                ? toSoapStructuredData(response)
                : toStructuredData(response.structuredJson());
        String rawText = response.rawText();
        String draftText = response.outputText();
        String structuredJson = response.structuredJson();
        if (taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING && soapTraceEnabled) {
            String traceId = RequestContextHolder.require().correlationId();
            String traceTenantId = RequestContextHolder.requireTenantId().toString();
            boolean structuredJsonPresent = structuredJson != null && !structuredJson.isBlank();
            boolean textLooksLikeJson = looksLikeJson(rawText) || looksLikeJson(draftText);
            String selectedSource = structuredJsonPresent ? "STRUCTURED_JSON" : "NONE";
            log.info("SOAP-DRAFT-TRACE stage=RESPONSE_SOURCE traceId={} tenantId={} consultationId={} patientId={} templateKey={} provider={} selectedSource={} structuredJsonTopLevelKeys={} textLooksLikeJson={} textJsonParsingAttempted=false textJsonParsingSucceeded=false parseExceptionType=null",
                    traceId,
                    traceTenantId,
                    consultationId,
                    patientId,
                    "clinic.consultation.structure-notes.v1",
                    response.provider(),
                    selectedSource,
                    structured.keySet(),
                    textLooksLikeJson);
            log.info("SOAP-DRAFT-TRACE stage=SOAP_EXTRACTED traceId={} tenantId={} consultationId={} patientId={} templateKey={} provider={} subjectivePresent={} subjectiveChars={} subjectivePlaceholder={} objectivePresent={} objectiveChars={} objectivePlaceholder={} assessmentPresent={} assessmentChars={} assessmentPlaceholder={} planPresent={} planChars={} planPlaceholder={} recognizedKeys={} unrecognizedKeys={} caseInsensitiveKeyMatchingUsed={} nestedSoapObjectDetected={}",
                    traceId,
                    traceTenantId,
                    consultationId,
                    patientId,
                    "clinic.consultation.structure-notes.v1",
                    response.provider(),
                    soapFieldState(structured, "subjective").present(),
                    soapFieldState(structured, "subjective").chars(),
                    soapFieldState(structured, "subjective").placeholder(),
                    soapFieldState(structured, "objective").present(),
                    soapFieldState(structured, "objective").chars(),
                    soapFieldState(structured, "objective").placeholder(),
                    soapFieldState(structured, "assessment").present(),
                    soapFieldState(structured, "assessment").chars(),
                    soapFieldState(structured, "assessment").placeholder(),
                    soapFieldState(structured, "plan").present(),
                    soapFieldState(structured, "plan").chars(),
                    soapFieldState(structured, "plan").placeholder(),
                    recognizedSoapKeys(structured),
                    unrecognizedSoapKeys(structured),
                    caseInsensitiveSoapKeyMatchingUsed(structured),
                    nestedSoapObjectDetected(structured));
            if (soapTraceRawResponseEnabled) {
                String rawDiagnostic = firstNonBlank(response.rawText(), response.outputText());
                log.info("SOAP-DRAFT-TRACE stage=RAW_RESPONSE_DEBUG traceId={} tenantId={} consultationId={} patientId={} templateKey={} provider={} rawResponseChars={} rawResponsePreview=\"{}\"",
                        traceId,
                        traceTenantId,
                        consultationId,
                        patientId,
                        "clinic.consultation.structure-notes.v1",
                        response.provider(),
                        rawDiagnostic == null ? 0 : rawDiagnostic.length(),
                        trimTo(rawDiagnostic, 8000));
            }
        }
        log.info("[NORMALIZED] taskType={} provider={} model={} rawChars={} outputChars={} structuredChars={} responseChars={} finishReason={} normalizedFinishReason={} parseStatus={} first300Chars=\"{}\" last300Chars=\"{}\"",
                taskType,
                response.provider(),
                response.model(),
                rawText == null ? 0 : rawText.length(),
                draftText == null ? 0 : draftText.length(),
                structuredJson == null ? 0 : structuredJson.length(),
                response.responseChars(),
                response.finishReason(),
                response.normalizedFinishReason(),
                response.parseStatus(),
                previewStart(rawText),
                previewEnd(rawText));
        log.info("[AI-RESPONSE-TOKENS] taskType={} provider={} model={} promptTokens={} completionTokens={} totalTokens={} estimatedCost={} finishReason={} normalizedFinishReason={}",
                taskType,
                response.provider(),
                response.model(),
                response.tokenUsage() == null ? null : response.tokenUsage().promptTokens(),
                response.tokenUsage() == null ? null : response.tokenUsage().completionTokens(),
                response.tokenUsage() == null ? null : response.tokenUsage().totalTokens(),
                response.tokenUsage() == null ? null : response.tokenUsage().estimatedCost(),
                response.finishReason(),
                response.normalizedFinishReason());
        log.debug("{} requestId={} provider={} model={} draftChars={} structuredKeys={} fallbackUsed={}",
                responsePrefix(taskType),
                response.requestId(),
                response.provider(),
                response.model(),
                response.outputText() == null ? 0 : response.outputText().length(),
                structured.keySet(),
                response.fallbackUsed());
        return new AiDraftResponse(
                true,
                response.fallbackUsed(),
                response.fallbackUsed() ? "AI fallback response used; verify manually." : "AI draft generated.",
                response.provider(),
                response.model(),
                response.outputText(),
                structured,
                response.confidence(),
                response.suggestedActions() == null ? List.of() : response.suggestedActions(),
                List.copyOf(warnings),
                response.finishReason(),
                response.normalizedFinishReason(),
                response.responseChars(),
                response.rawText(),
                taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING
                        ? (structured.isEmpty()
                        ? ("TRUNCATED".equalsIgnoreCase(response.normalizedFinishReason()) ? "TRUNCATED" : "FAILED")
                        : "VALID")
                        : response.parseStatus()
        );
    }

    private boolean isConsultationAsk(String promptTemplateCode, String useCaseCode) {
        return CONSULTATION_ASK_TEMPLATE_CODE.equalsIgnoreCase(normalize(promptTemplateCode))
                || CONSULTATION_ASK_USE_CASE.equalsIgnoreCase(normalize(useCaseCode));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private Map<String, Object> toStructuredData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? Map.of("raw", json) : new LinkedHashMap<>(parsed);
        } catch (Exception ex) {
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("raw", json);
            return structured;
        }
    }

    private Map<String, Object> toSoapStructuredData(AiOrchestrationResponse response) {
        Map<String, Object> structuredFromResponse = extractSoapStructuredData(toStructuredData(response == null ? null : response.structuredJson()));
        if (!structuredFromResponse.isEmpty()) {
            return structuredFromResponse;
        }
        String candidateText = firstNonBlank(response == null ? null : response.rawText(), response == null ? null : response.outputText());
        Map<String, Object> structuredFromTextJson = extractSoapStructuredData(toStructuredData(extractJsonCandidate(candidateText)));
        if (!structuredFromTextJson.isEmpty()) {
            return structuredFromTextJson;
        }
        return extractSoapStructuredDataFromMarkdown(candidateText);
    }

    private Map<String, Object> extractSoapStructuredData(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> nested = nestedSoapObject(source);
        Map<String, Object> soap = new LinkedHashMap<>();
        addSoapSection(soap, "subjective", normalizeSoapSectionText("subjective", source, nested));
        addSoapSection(soap, "objective", normalizeSoapSectionText("objective", source, nested));
        addSoapSection(soap, "assessment", normalizeSoapSectionText("assessment", source, nested));
        addSoapSection(soap, "plan", normalizeSoapSectionText("plan", source, nested));
        return soap;
    }

    private Map<String, Object> extractSoapStructuredDataFromMarkdown(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentSection = null;
        for (String rawLine : rawText.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || isSoapDisclaimerLine(line)) {
                continue;
            }
            String heading = canonicalSoapHeading(line);
            if (heading != null) {
                currentSection = heading;
                String remainder = headingRemainder(line);
                if (remainder != null) {
                    appendSoapSectionLine(sections, currentSection, remainder);
                }
                continue;
            }
            if (currentSection != null) {
                appendSoapSectionLine(sections, currentSection, line);
            }
        }
        Map<String, Object> soap = new LinkedHashMap<>();
        addSoapSection(soap, "subjective", joinSoapSectionLines(sections.get("subjective")));
        addSoapSection(soap, "objective", joinSoapSectionLines(sections.get("objective")));
        addSoapSection(soap, "assessment", joinSoapSectionLines(sections.get("assessment")));
        addSoapSection(soap, "plan", joinSoapSectionLines(sections.get("plan")));
        return soap;
    }

    private void appendSoapSectionLine(Map<String, List<String>> sections, String key, String value) {
        String cleaned = cleanSoapText(value);
        if (cleaned == null) {
            return;
        }
        sections.computeIfAbsent(key, ignored -> new ArrayList<>());
        List<String> lines = sections.get(key);
        if (!lines.contains(cleaned)) {
            lines.add(cleaned);
        }
    }

    private String joinSoapSectionLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        String text = String.join("\n", lines).trim();
        return cleanSoapText(text);
    }

    private void addSoapSection(Map<String, Object> soap, String key, String value) {
        String cleaned = cleanSoapText(value);
        if (cleaned != null) {
            soap.put(key, cleaned);
        }
    }

    private String normalizeSoapSectionText(String section, Map<String, Object> source, Map<String, Object> nested) {
        LinkedHashSet<String> fragments = new LinkedHashSet<>();
        collectSoapSectionFragments(section, source, fragments);
        collectSoapSectionFragments(section, nested, fragments);
        return truncateSoapSection(section, joinSoapFragments(section, fragments));
    }

    private void collectSoapSectionFragments(String section, Map<String, Object> source, LinkedHashSet<String> fragments) {
        if (source == null || source.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String normalizedKey = normalizeSoapKey(entry.getKey());
            if (!belongsToSoapSection(section, normalizedKey)) {
                continue;
            }
            entries.add(entry);
        }
        entries.sort((left, right) -> Integer.compare(
                soapKeyPriority(section, normalizeSoapKey(left.getKey())),
                soapKeyPriority(section, normalizeSoapKey(right.getKey()))));
        for (Map.Entry<String, Object> entry : entries) {
            collectSoapEntryFragments(section, normalizeSoapKey(entry.getKey()), entry.getValue(), fragments);
        }
    }

    private void collectSoapEntryFragments(String section, String normalizedKey, Object value, LinkedHashSet<String> fragments) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) map);
            if (normalizedKey.equals("vitals") && "objective".equals(section)) {
                addSoapFragment(fragments, formatVitalsFragment(typed));
                return;
            }
            List<Map.Entry<String, Object>> nestedEntries = new ArrayList<>(typed.entrySet());
            nestedEntries.sort((left, right) -> Integer.compare(
                    soapKeyPriority(section, normalizeSoapKey(left.getKey())),
                    soapKeyPriority(section, normalizeSoapKey(right.getKey()))));
            for (Map.Entry<String, Object> nestedEntry : nestedEntries) {
                collectSoapEntryFragments(section, normalizeSoapKey(nestedEntry.getKey()), nestedEntry.getValue(), fragments);
            }
            return;
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                String cleaned = cleanSoapText(item == null ? null : String.valueOf(item));
                if (cleaned != null && !items.contains(cleaned)) {
                    items.add(cleaned);
                }
            }
            if (!items.isEmpty()) {
                addSoapFragment(fragments, formatSoapScalarFragment(section, normalizedKey, String.join("; ", items)));
            }
            return;
        }
        addSoapFragment(fragments, formatSoapScalarFragment(section, normalizedKey, String.valueOf(value)));
    }

    private void addSoapFragment(LinkedHashSet<String> fragments, String value) {
        String cleaned = cleanSoapText(value);
        if (cleaned == null) {
            return;
        }
        String signature = fragmentSignature(cleaned);
        if (signature.isBlank()) {
            return;
        }
        for (String existing : fragments) {
            if (fragmentSignature(existing).equals(signature)) {
                return;
            }
        }
        fragments.add(cleaned);
    }

    private String joinSoapFragments(String section, LinkedHashSet<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>(fragments);
        if ("objective".equals(section)) {
            return String.join("; ", ordered).trim();
        }
        return String.join(" ", ordered).replaceAll("\\s{2,}", " ").trim();
    }

    private String truncateSoapSection(String section, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int maxChars = switch (section) {
            case "objective" -> 600;
            case "subjective", "assessment", "plan" -> 800;
            default -> 800;
        };
        if (text.length() <= maxChars) {
            return cleanSoapText(text);
        }
        int cut = lastBoundaryBefore(text, maxChars);
        String truncated = cut > 0 ? text.substring(0, cut).trim() : text.substring(0, maxChars).trim();
        return cleanSoapText(truncated);
    }

    private int lastBoundaryBefore(String text, int maxChars) {
        int limit = Math.min(maxChars, text.length());
        int boundary = -1;
        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);
            if (ch == '.' || ch == ';' || ch == '\n') {
                boundary = i + 1;
            }
        }
        if (boundary > 0) {
            return boundary;
        }
        for (int i = limit - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private String fragmentSignature(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase().trim();
        normalized = normalized.replaceFirst("^(patient reports|symptoms include|known|allergy|allergies|current medications include|differentials include|abnormal findings|relevant risks|recommendations|monitoring|investigations|follow-up|followup|safety net|advice|bp|rr|temp|spo2|bmi|rbs|pain score)\\s*[:.-]?\\s*", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private int soapKeyPriority(String section, String normalizedKey) {
        if (section == null || normalizedKey == null) {
            return 100;
        }
        return switch (section) {
            case "subjective" -> {
                if (normalizedKey.equals("subjective") || normalizedKey.equals("presentingillness") || normalizedKey.equals("chiefcomplaint") || normalizedKey.equals("chiefcomplaints")) {
                    yield 0;
                }
                if (normalizedKey.equals("symptoms") || normalizedKey.equals("associatedsymptoms")) {
                    yield 1;
                }
                if (normalizedKey.equals("negativesymptoms")) {
                    yield 2;
                }
                if (normalizedKey.equals("history") || normalizedKey.equals("historyofpresentillness")) {
                    yield 3;
                }
                if (normalizedKey.equals("pastmedicalhistory") || normalizedKey.equals("chronicconditions")) {
                    yield 4;
                }
                if (normalizedKey.equals("allergies")) {
                    yield 5;
                }
                if (normalizedKey.equals("medications") || normalizedKey.equals("medicationhistory")) {
                    yield 6;
                }
                yield 7;
            }
            case "objective" -> {
                if (normalizedKey.equals("vitals")) {
                    yield 0;
                }
                if (normalizedKey.equals("bloodpressure") || normalizedKey.equals("bloodpressuresystolic") || normalizedKey.equals("bloodpressurediastolic") || normalizedKey.equals("pulse") || normalizedKey.equals("heartrate") || normalizedKey.equals("respiratoryrate") || normalizedKey.equals("temperature") || normalizedKey.equals("temperatureunit") || normalizedKey.equals("spo2") || normalizedKey.equals("oxygenaturation") || normalizedKey.equals("bmi") || normalizedKey.equals("randombloodsugar") || normalizedKey.equals("painscore")) {
                    yield 1;
                }
                if (normalizedKey.equals("examination") || normalizedKey.equals("examinationfindings") || normalizedKey.equals("findings")) {
                    yield 2;
                }
                if (normalizedKey.equals("observations")) {
                    yield 3;
                }
                if (normalizedKey.equals("labs") || normalizedKey.equals("imaging") || normalizedKey.equals("investigations")) {
                    yield 4;
                }
                yield 5;
            }
            case "assessment" -> {
                if (normalizedKey.equals("diagnosis") || normalizedKey.equals("primarydiagnosis") || normalizedKey.equals("assessment")) {
                    yield 0;
                }
                if (normalizedKey.equals("impression") || normalizedKey.equals("summary")) {
                    yield 1;
                }
                if (normalizedKey.equals("abnormalfindings")) {
                    yield 2;
                }
                if (normalizedKey.equals("relevantrisks")) {
                    yield 3;
                }
                if (normalizedKey.equals("clinicalinterpretation")) {
                    yield 4;
                }
                yield 5;
            }
            case "plan" -> {
                if (normalizedKey.equals("plan") || normalizedKey.equals("treatment") || normalizedKey.equals("management") || normalizedKey.equals("treatmentplan") || normalizedKey.equals("managementplan")) {
                    yield 0;
                }
                if (normalizedKey.equals("recommendations")) {
                    yield 1;
                }
                if (normalizedKey.equals("monitoring")) {
                    yield 2;
                }
                if (normalizedKey.equals("investigations")) {
                    yield 3;
                }
                if (normalizedKey.equals("followup")) {
                    yield 4;
                }
                if (normalizedKey.equals("safetynetting")) {
                    yield 5;
                }
                if (normalizedKey.equals("patientadvice")) {
                    yield 6;
                }
                yield 7;
            }
            default -> 100;
        };
    }

    private boolean belongsToSoapSection(String section, String normalizedKey) {
        if (section == null || normalizedKey == null) {
            return false;
        }
        return switch (section) {
            case "subjective" -> normalizedKey.equals("subjective")
                    || normalizedKey.equals("s")
                    || normalizedKey.equals("history")
                    || normalizedKey.equals("chiefcomplaint")
                    || normalizedKey.equals("chiefcomplaints")
                    || normalizedKey.equals("presentingillness")
                    || normalizedKey.equals("historyofpresentillness")
                    || normalizedKey.equals("symptoms")
                    || normalizedKey.equals("associatedsymptoms")
                    || normalizedKey.equals("negativesymptoms")
                    || normalizedKey.equals("pastmedicalhistory")
                    || normalizedKey.equals("chronicconditions")
                    || normalizedKey.equals("allergies")
                    || normalizedKey.equals("medications")
                    || normalizedKey.equals("medicationhistory")
                    || normalizedKey.equals("socialhistory")
                    || normalizedKey.equals("patientreportedinformation");
            case "objective" -> normalizedKey.equals("objective")
                    || normalizedKey.equals("o")
                    || normalizedKey.equals("examination")
                    || normalizedKey.equals("examinationfindings")
                    || normalizedKey.equals("findings")
                    || normalizedKey.equals("vitals")
                    || normalizedKey.equals("bloodpressure")
                    || normalizedKey.equals("bloodpressuresystolic")
                    || normalizedKey.equals("bloodpressurediastolic")
                    || normalizedKey.equals("pulse")
                    || normalizedKey.equals("heartrate")
                    || normalizedKey.equals("respiratoryrate")
                    || normalizedKey.equals("temperature")
                    || normalizedKey.equals("temperatureunit")
                    || normalizedKey.equals("spo2")
                    || normalizedKey.equals("oxygenaturation")
                    || normalizedKey.equals("bmi")
                    || normalizedKey.equals("randombloodsugar")
                    || normalizedKey.equals("painscore")
                    || normalizedKey.equals("observations")
                    || normalizedKey.equals("labs")
                    || normalizedKey.equals("imaging")
                    || normalizedKey.equals("investigations");
            case "assessment" -> normalizedKey.equals("assessment")
                    || normalizedKey.equals("a")
                    || normalizedKey.equals("diagnosis")
                    || normalizedKey.equals("primarydiagnosis")
                    || normalizedKey.equals("impression")
                    || normalizedKey.equals("summary")
                    || normalizedKey.equals("differentials")
                    || normalizedKey.equals("abnormalfindings")
                    || normalizedKey.equals("relevantrisks")
                    || normalizedKey.equals("clinicalinterpretation");
            case "plan" -> normalizedKey.equals("plan")
                    || normalizedKey.equals("p")
                    || normalizedKey.equals("treatment")
                    || normalizedKey.equals("management")
                    || normalizedKey.equals("treatmentplan")
                    || normalizedKey.equals("recommendations")
                    || normalizedKey.equals("managementplan")
                    || normalizedKey.equals("monitoring")
                    || normalizedKey.equals("investigations")
                    || normalizedKey.equals("followup")
                    || normalizedKey.equals("safetynetting")
                    || normalizedKey.equals("patientadvice");
            default -> false;
        };
    }

    private String formatSoapScalarFragment(String section, String normalizedKey, String rawText) {
        String text = cleanSoapText(rawText);
        if (text == null) {
            return null;
        }
        return switch (section) {
            case "subjective" -> formatSubjectiveFragment(normalizedKey, text);
            case "objective" -> formatObjectiveFragment(normalizedKey, text);
            case "assessment" -> formatAssessmentFragment(normalizedKey, text);
            case "plan" -> formatPlanFragment(normalizedKey, text);
            default -> ensureSentence(text);
        };
    }

    private String formatSubjectiveFragment(String normalizedKey, String text) {
        return switch (normalizedKey) {
            case "subjective", "history" ->
                    ensureSentence(cleanSoapText(text));
            case "presentingillness", "chiefcomplaint", "chiefcomplaints", "historyofpresentillness" ->
                    ensureSentence(prefaceIfNeeded(text, "Patient reports "));
            case "symptoms", "associatedsymptoms" ->
                    ensureSentence(prefaceIfNeeded(text, "Symptoms include "));
            case "negativesymptoms" ->
                    ensureSentence(prefaceNegativeSymptoms(text));
            case "pastmedicalhistory", "chronicconditions" ->
                    ensureSentence(prefaceIfNeeded(text, "Known "));
            case "allergies" ->
                    ensureSentence(prefaceIfNeeded(text, "Allergy: "));
            case "medications", "medicationhistory" ->
                    ensureSentence(prefaceIfNeeded(text, "Current medications include "));
            case "socialhistory", "patientreportedinformation" ->
                    ensureSentence(text);
            default -> ensureSentence(text);
        };
    }

    private String formatObjectiveFragment(String normalizedKey, String text) {
        return switch (normalizedKey) {
            case "objective", "examination", "examinationfindings", "findings", "observations", "labs", "imaging", "investigations" ->
                    ensureSentence(cleanSoapText(text));
            case "bloodpressure", "bloodpressuresystolic", "bloodpressurediastolic" -> ensureSentence(prefaceIfNeeded(text, "BP "));
            case "pulse", "heartrate" -> ensureSentence(prefaceMetric(text, "pulse "));
            case "respiratoryrate" -> ensureSentence(prefaceMetric(text, "RR "));
            case "temperature" -> ensureSentence(prefaceTemperature(text));
            case "temperatureunit" -> null;
            case "spo2", "oxygenaturation" -> ensureSentence(prefaceMetric(text, "SpO2 "));
            case "bmi" -> ensureSentence(prefaceMetric(text, "BMI "));
            case "randombloodsugar" -> ensureSentence(prefaceMetric(text, "RBS "));
            case "painscore" -> ensureSentence(prefaceIfNeeded(text, "pain score "));
            case "vitals" -> ensureSentence(text);
            default -> ensureSentence(text);
        };
    }

    private String formatAssessmentFragment(String normalizedKey, String text) {
        return switch (normalizedKey) {
            case "assessment", "diagnosis", "primarydiagnosis", "impression", "summary" -> ensureSentence(cleanSoapText(text));
            case "differentials" -> ensureSentence(prefaceIfNeeded(text, "Differentials include "));
            case "abnormalfindings" -> ensureSentence(prefaceIfNeeded(text, "Abnormal findings: "));
            case "relevantrisks" -> ensureSentence(prefaceIfNeeded(text, "Relevant risks: "));
            case "clinicalinterpretation" -> ensureSentence(text);
            default -> ensureSentence(text);
        };
    }

    private String formatPlanFragment(String normalizedKey, String text) {
        return switch (normalizedKey) {
            case "plan", "treatment", "management", "treatmentplan", "managementplan" -> ensureSentence(cleanSoapText(text));
            case "recommendations" -> ensureSentence(prefaceIfNeeded(text, "Recommendations: "));
            case "monitoring" -> ensureSentence(prefaceIfNeeded(text, "Monitoring: "));
            case "investigations" -> ensureSentence(prefaceIfNeeded(text, "Investigations: "));
            case "followup" -> ensureSentence(prefaceIfNeeded(text, "Follow-up: "));
            case "safetynetting" -> ensureSentence(prefaceIfNeeded(text, "Safety net: "));
            case "patientadvice" -> ensureSentence(prefaceIfNeeded(text, "Advice: "));
            default -> ensureSentence(text);
        };
    }

    private String prefaceIfNeeded(String text, String prefix) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.toLowerCase().startsWith(prefix.trim().toLowerCase())) {
            return normalized;
        }
        return prefix + normalized;
    }

    private String prefaceNegativeSymptoms(String text) {
        String normalized = text.trim();
        String lower = normalized.toLowerCase();
        if (lower.startsWith("no ") || lower.startsWith("denies ") || lower.startsWith("without ")) {
            return normalized;
        }
        return "No " + normalized;
    }

    private String prefaceMetric(String text, String prefix) {
        String normalized = text.trim();
        if (normalized.toLowerCase().startsWith(prefix.trim().toLowerCase())) {
            return normalized;
        }
        return prefix + normalized;
    }

    private String prefaceTemperature(String text) {
        String normalized = text.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("°c") || lower.contains("celsius") || lower.contains("fahrenheit")) {
            return normalized;
        }
        return "Temp " + normalized;
    }

    private String ensureSentence(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("\\s{2,}", " ").trim();
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.endsWith(".") || cleaned.endsWith(";") || cleaned.endsWith(":")) {
            return cleaned;
        }
        return cleaned + ".";
    }

    private String formatVitalsFragment(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<String> fragments = new ArrayList<>();
        String systolic = soapMapText(source, "blood_pressure_systolic", "bloodPressureSystolic", "systolic");
        String diastolic = soapMapText(source, "blood_pressure_diastolic", "bloodPressureDiastolic", "diastolic");
        String bloodPressure = soapMapText(source, "blood_pressure", "bloodPressure", "bp");
        if (bloodPressure == null && systolic != null && diastolic != null) {
            bloodPressure = systolic + "/" + diastolic;
        }
        if (bloodPressure != null) {
            fragments.add("BP " + bloodPressure + (bloodPressure.toLowerCase().contains("mmhg") ? "" : " mmHg"));
        }
        String pulse = soapMapText(source, "pulse", "pulse_rate", "heartrate");
        if (pulse != null) {
            fragments.add("pulse " + pulse + (pulse.toLowerCase().contains("bpm") ? "" : " bpm"));
        }
        String rr = soapMapText(source, "respiratory_rate", "respiratoryRate", "rr");
        if (rr != null) {
            fragments.add("RR " + rr + (rr.toLowerCase().contains("/min") || rr.toLowerCase().contains("breaths") ? "" : "/min"));
        }
        String temp = soapMapText(source, "temperature", "temp");
        String tempUnit = soapMapText(source, "temperature_unit", "temperatureUnit");
        if (temp != null) {
            String temperature = temp;
            if (tempUnit != null && !temperature.toLowerCase().contains(tempUnit.toLowerCase())) {
                temperature = temperature + " " + tempUnit;
            }
            fragments.add("temperature " + temperature);
        }
        String spo2 = soapMapText(source, "spo2", "oxygen_saturation", "oxygenSaturation");
        if (spo2 != null) {
            fragments.add("SpO2 " + spo2 + (spo2.contains("%") ? "" : "%"));
        }
        String bmi = soapMapText(source, "bmi");
        if (bmi != null) {
            fragments.add("BMI " + bmi);
        }
        String rbs = soapMapText(source, "random_blood_sugar", "randomBloodSugar", "rbs");
        if (rbs != null) {
            fragments.add("RBS " + rbs + (rbs.toLowerCase().contains("mg/dl") ? "" : " mg/dL"));
        }
        String pain = soapMapText(source, "pain_score", "painScore");
        if (pain != null) {
            fragments.add("pain score " + pain + (pain.contains("/10") ? "" : "/10"));
        }
        String text = String.join("; ", fragments).trim();
        return text.isBlank() ? null : text;
    }

    private String soapMapText(Map<String, Object> source, String... aliases) {
        if (source == null || source.isEmpty() || aliases == null || aliases.length == 0) {
            return null;
        }
        for (String alias : aliases) {
            String normalizedAlias = normalizeSoapKey(alias);
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey() == null || !normalizeSoapKey(entry.getKey()).equals(normalizedAlias)) {
                    continue;
                }
                String value = cleanSoapText(entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String firstSoapValue(Map<String, Object> source, Map<String, Object> nested, String canonical, String... aliases) {
        String value = soapValueFromMap(source, canonical);
        if (value == null) {
            value = soapValueFromMap(nested, canonical);
        }
        if (value != null) {
            return value;
        }
        List<String> values = new ArrayList<>();
        collectSoapAliasValues(values, source, aliases);
        collectSoapAliasValues(values, nested, aliases);
        if (values.isEmpty()) {
            return null;
        }
        return cleanSoapText(String.join("\n", values));
    }

    private void collectSoapAliasValues(List<String> values, Map<String, Object> source, String... aliases) {
        if (source == null || source.isEmpty() || aliases == null) {
            return;
        }
        for (String alias : aliases) {
            String value = soapValueFromMap(source, alias);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
    }

    private Map<String, Object> nestedSoapObject(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && normalizeSoapKey(entry.getKey()).equals("soap") && entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) nested);
                return typed;
            }
        }
        return Map.of();
    }

    private String soapValueFromMap(Map<String, Object> source, String alias) {
        if (source == null || source.isEmpty() || alias == null || alias.isBlank()) {
            return null;
        }
        String normalizedAlias = normalizeSoapKey(alias);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || normalizeSoapKey(entry.getKey()).isEmpty()) {
                continue;
            }
            if (!normalizeSoapKey(entry.getKey()).equals(normalizedAlias)) {
                continue;
            }
            String value = cleanSoapText(entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeSoapKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private boolean isSoapDisclaimerLine(String line) {
        if (line == null) {
            return false;
        }
        String normalized = normalizeSoapKey(line);
        return normalized.contains("thisisanaigenerateddraft") || normalized.contains("doctormustverifybeforeuse");
    }

    private String canonicalSoapHeading(String line) {
        String normalized = normalizeSoapKey(line);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.startsWith("1subjective") || normalized.startsWith("subjective") || normalized.startsWith("history") || normalized.startsWith("chiefcomplaint") || normalized.startsWith("symptoms")) {
            return "subjective";
        }
        if (normalized.startsWith("2objective") || normalized.startsWith("objective") || normalized.startsWith("examination") || normalized.startsWith("findings") || normalized.startsWith("vitals")) {
            return "objective";
        }
        if (normalized.startsWith("3assessment") || normalized.startsWith("assessment") || normalized.startsWith("diagnosis") || normalized.startsWith("impression")) {
            return "assessment";
        }
        if (normalized.startsWith("4plan") || normalized.startsWith("plan") || normalized.startsWith("recommendations") || normalized.startsWith("managementplan") || normalized.startsWith("treatmentplan")) {
            return "plan";
        }
        return null;
    }

    private String headingRemainder(String line) {
        if (line == null) {
            return null;
        }
        String cleaned = line.trim();
        cleaned = cleaned.replaceFirst("(?i)^#{1,6}\\s*", "");
        cleaned = cleaned.replaceFirst("(?i)^\\d+(?:\\.\\d+)*[\\)\\.]?\\s*", "");
        cleaned = cleaned.replace("*", "");
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }
        String heading = cleaned.substring(0, colonIndex).trim();
        if (canonicalSoapHeading(heading) == null) {
            return null;
        }
        return cleanSoapText(cleaned.substring(colonIndex + 1));
    }

    private String cleanSoapText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\t]+", " ").trim();
        cleaned = cleaned.replace(SAFETY_NOTICE, "").trim();
        cleaned = cleaned.replaceAll("(?i)^this is an ai-generated draft\\.?\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^doctor must verify before use\\.?\\s*", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        if (cleaned.isBlank()) {
            return null;
        }
        String normalized = cleaned.toLowerCase().trim();
        if (normalized.equals("-")
                || normalized.equals("--")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("unknown")
                || normalized.equals("not available")
                || normalized.equals("not documented")) {
            return null;
        }
        return cleaned;
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private String soapSectionValue(Map<String, Object> structured, String key) {
        if (structured == null || structured.isEmpty()) {
            return null;
        }
        Object value = structured.get(key);
        if (value == null) {
            value = structured.get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replaceAll("[\\r\\n\\t]+", " ").trim();
        return text.isBlank() ? null : text;
    }

    private SoapFieldState soapFieldState(Map<String, Object> structured, String key) {
        String raw = soapRawSectionValue(structured, key);
        String value = soapSectionValue(structured, key);
        return new SoapFieldState(value != null && !value.isBlank(), value == null ? 0 : value.length(), isSoapPlaceholderValue(raw));
    }

    private List<String> recognizedSoapKeys(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String key : structured.keySet()) {
            if (isRecognizedSoapKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> unrecognizedSoapKeys(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String key : structured.keySet()) {
            if (!isRecognizedSoapKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private boolean caseInsensitiveSoapKeyMatchingUsed(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return false;
        }
        for (String key : structured.keySet()) {
            if (isRecognizedSoapKey(key) && !recognizedSoapKeyExactMatch(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean recognizedSoapKeyExactMatch(String key) {
        return "subjective".equals(key) || "objective".equals(key) || "assessment".equals(key) || "plan".equals(key);
    }

    private boolean nestedSoapObjectDetected(Map<String, Object> structured) {
        if (structured == null || structured.isEmpty()) {
            return false;
        }
        Object nested = structured.get("soap");
        if (!(nested instanceof Map<?, ?>)) {
            nested = structured.get("SOAP");
        }
        return nested instanceof Map<?, ?>;
    }

    private boolean isSoapPlaceholderValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank()
                || normalized.equals("-")
                || normalized.equals("--")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("not available")
                || normalized.equals("not documented");
    }

    private String soapRawSectionValue(Map<String, Object> structured, String key) {
        if (structured == null || structured.isEmpty()) {
            return null;
        }
        Object value = structured.get(key);
        if (value == null) {
            value = structured.get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        return value == null ? null : String.valueOf(value);
    }

    private boolean looksLikeJson(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.contains("\"subjective\"");
    }

    private String safeId(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimTo(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private boolean isRecognizedSoapKey(String key) {
        return key != null && ("subjective".equalsIgnoreCase(key) || "objective".equalsIgnoreCase(key) || "assessment".equalsIgnoreCase(key) || "plan".equalsIgnoreCase(key));
    }

    private record SoapFieldState(boolean present, int chars, boolean placeholder) {}

    private AiDraftResponse disabledResponse() {
        return new AiDraftResponse(
                false,
                false,
                "AI copilot is disabled for this environment.",
                null,
                null,
                null,
                Map.of(),
                null,
                List.of(),
                List.of(SAFETY_NOTICE),
                null,
                "UNKNOWN",
                0,
                null,
                "FAILED"
        );
    }

    private String responsePrefix(AiTaskType taskType) {
        if (taskType == AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT) {
            return "AI_DIAGNOSIS_RESPONSE";
        }
        if (taskType == AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION) {
            return "AI_MEDICINE_RESPONSE";
        }
        if (taskType == AiTaskType.CLINICAL_REASONING) {
            return "AI_REASONING_RESPONSE";
        }
        return "AI_RESPONSE";
    }
}
