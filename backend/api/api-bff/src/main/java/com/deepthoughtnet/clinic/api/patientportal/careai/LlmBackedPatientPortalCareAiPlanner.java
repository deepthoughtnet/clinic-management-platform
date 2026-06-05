package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LlmBackedPatientPortalCareAiPlanner implements PatientPortalCareAiPlanner {
    private static final Logger log = LoggerFactory.getLogger(LlmBackedPatientPortalCareAiPlanner.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String AI_EXTRACTION_TEMPLATE_CODE = "generic.extraction.v1";
    private static final String AI_EXTRACTION_USE_CASE = "patient-portal-careai-extraction";
    private static final double AI_EXTRACTION_TEMPERATURE = 0.2d;

    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final VoiceTestProperties voiceTestProperties;
    private final boolean aiExtractionEnabled;

    public LlmBackedPatientPortalCareAiPlanner(
            AiOrchestrationService aiOrchestrationService,
            ObjectMapper objectMapper,
            VoiceTestProperties voiceTestProperties,
            @Value("${clinic.ai.enabled:false}") boolean aiExtractionEnabled
    ) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.voiceTestProperties = voiceTestProperties;
        this.aiExtractionEnabled = aiExtractionEnabled;
    }

    @Override
    public PatientPortalCareAiPlannerDecision plan(PatientPortalCareAiPlanningContext context) {
        if (!aiExtractionEnabled || aiOrchestrationService == null || context == null || !StringUtils.hasText(context.latestMessage())) {
            return null;
        }
        try {
            var requestContext = RequestContextHolder.require();
            Map<String, Object> inputVariables = new LinkedHashMap<>();
            inputVariables.put("message", context.latestMessage());
            inputVariables.put("language", safe(context.language()));
            inputVariables.put("currentIntent", safe(context.currentIntent()));
            inputVariables.put("confirmationPending", context.confirmationPending());
            inputVariables.put("pendingAction", safe(context.pendingAction()));
            inputVariables.put("requestedDoctorName", safe(context.requestedDoctorName()));
            inputVariables.put("selectedDoctorName", safe(context.selectedDoctorName()));
            inputVariables.put("requestedSpeciality", safe(context.requestedSpeciality()));
            inputVariables.put("selectedAppointmentLabel", safe(context.selectedAppointmentLabel()));
            inputVariables.put("preferredDate", safe(context.preferredDate()));
            inputVariables.put("preferredTimeWindow", safe(context.preferredTimeWindow()));
            inputVariables.put("selectedSlot", safe(context.selectedSlot()));
            inputVariables.put("missingFields", context.missingFields());
            inputVariables.put("availableActions", context.availableActions());
            inputVariables.put("doctorOptions", context.doctorOptions());
            inputVariables.put("appointmentOptions", context.appointmentOptions());
            inputVariables.put("slotOptions", context.slotOptions());
            inputVariables.put("knownDoctorOptions", context.knownDoctorNames());
            inputVariables.put("today", LocalDate.now().toString());
            inputVariables.put("instructions", """
                    You are a planning assistant for a patient appointment workflow.
                    Use the current conversation state and latest user message to infer the next safe planning update only.
                    Return strict JSON with keys:
                    intent, doctorName, speciality, preferredDate, preferredTimeWindow, confirmation, reason, topicSwitch.
                    intent must be one of BOOK_APPOINTMENT, RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT, APPOINTMENT_STATUS, or null.
                    preferredDate should be yyyy-MM-dd when confidently known, otherwise null.
                    preferredTimeWindow may be morning, afternoon, evening, night, before lunch, after lunch, now, HH:mm, or null.
                    confirmation must be confirm, reject, or null.
                    topicSwitch must be true only if the patient is clearly abandoning the current booking topic.
                    Do not execute actions. Do not invent patient identity. Do not return medical advice, diagnosis, or irreversible actions.
                    If the user is answering a missing field from the current state, prefer filling that field over repeating the same question.
                    """);
            AiOrchestrationResponse response = aiOrchestrationService.complete(new AiOrchestrationRequest(
                    AiProductCode.GENERIC,
                    RequestContextHolder.requireTenantId(),
                    requestContext.appUserId(),
                    AiTaskType.GENERIC_EXTRACTION,
                    AI_EXTRACTION_TEMPLATE_CODE,
                    inputVariables,
                    List.of(),
                    aiExtractionMaxTokens(),
                    AI_EXTRACTION_TEMPERATURE,
                    requestContext.correlationId(),
                    AI_EXTRACTION_USE_CASE
            ));
            return parseDecision(response);
        } catch (RuntimeException ex) {
            log.debug("patient.portal.careai.planner_failed messageHash={} reason={}",
                    Integer.toHexString(context.latestMessage().hashCode()),
                    ex.toString());
            return null;
        }
    }

    private PatientPortalCareAiPlannerDecision parseDecision(AiOrchestrationResponse response) {
        if (response == null) {
            return null;
        }
        try {
            Map<String, Object> structured = payload(response);
            if (structured == null || structured.isEmpty()) {
                return null;
            }
            PatientPortalCareAiPlannerDecision decision = new PatientPortalCareAiPlannerDecision(
                    parseIntent(stringValue(structured, "intent")),
                    cleanName(stringValue(structured, "doctorName")),
                    trimToLength(stringValue(structured, "speciality"), 80),
                    trimToLength(stringValue(structured, "preferredDate"), 32),
                    trimToLength(stringValue(structured, "preferredTimeWindow"), 32),
                    parseConfirmation(stringValue(structured, "confirmation")),
                    trimToLength(stringValue(structured, "reason"), 160),
                    parseTopicSwitch(structured.get("topicSwitch"))
            );
            return isActionable(decision) ? decision : null;
        } catch (Exception ex) {
            log.debug("patient.portal.careai.planner_parse_failed requestId={} reason={}", response.requestId(), ex.toString());
            return null;
        }
    }

    private boolean isActionable(PatientPortalCareAiPlannerDecision decision) {
        return decision != null
                && (decision.intent() != null
                || StringUtils.hasText(decision.doctorName())
                || StringUtils.hasText(decision.speciality())
                || StringUtils.hasText(decision.preferredDate())
                || StringUtils.hasText(decision.preferredTimeWindow())
                || decision.confirmationDecision() != PatientPortalCareAiPlannerConfirmationDecision.NONE
                || StringUtils.hasText(decision.reason())
                || decision.topicSwitch());
    }

    private Map<String, Object> payload(AiOrchestrationResponse response) throws Exception {
        if (StringUtils.hasText(response.structuredJson())) {
            return objectMapper.readValue(response.structuredJson(), MAP_TYPE);
        }
        String output = response.outputText();
        if (looksLikeJsonObject(output)) {
            return objectMapper.readValue(output, MAP_TYPE);
        }
        return null;
    }

    private boolean looksLikeJsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            return firstBrace >= 0 && lastBrace > firstBrace;
        }
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private int aiExtractionMaxTokens() {
        int configured = voiceTestProperties == null ? 0 : voiceTestProperties.getLlm().getMaxOutputTokens();
        return configured > 0 ? configured : 1024;
    }

    private PatientPortalCareAiIntent parseIntent(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return PatientPortalCareAiIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PatientPortalCareAiPlannerConfirmationDecision parseConfirmation(String value) {
        if (!StringUtils.hasText(value)) {
            return PatientPortalCareAiPlannerConfirmationDecision.NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("confirm", "confirmed", "yes", "approve").contains(normalized)) {
            return PatientPortalCareAiPlannerConfirmationDecision.CONFIRM;
        }
        if (List.of("reject", "decline", "no", "change").contains(normalized)) {
            return PatientPortalCareAiPlannerConfirmationDecision.REJECT;
        }
        return PatientPortalCareAiPlannerConfirmationDecision.NONE;
    }

    private boolean parseTopicSwitch(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (rawValue == null) {
            return false;
        }
        String normalized = String.valueOf(rawValue).trim().toLowerCase(Locale.ROOT);
        return List.of("true", "yes", "switch", "switch_topic").contains(normalized);
    }

    private String stringValue(Map<String, Object> structured, String key) {
        Object value = structured.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.replaceAll("[^\\p{L} .'-]", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
