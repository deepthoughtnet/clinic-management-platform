package com.deepthoughtnet.clinic.realtime.voice.receptionist;

import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Controlled v1 AI receptionist workflow service.
 * This component persists state in session metadata and keeps outputs auditable.
 */
@Service
public class AiReceptionistWorkflowService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?[0-9][0-9\\-\\s]{7,15}[0-9])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("\\b(?:i am|i'm|this is|name is)\\s+([a-zA-Z][a-zA-Z\\s]{1,60})", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final LeadService leadService;
    private final LeadActivityService leadActivityService;
    private final ClinicProfileService clinicProfileService;

    public AiReceptionistWorkflowService(
            ObjectMapper objectMapper,
            LeadService leadService,
            LeadActivityService leadActivityService,
            ClinicProfileService clinicProfileService
    ) {
        this.objectMapper = objectMapper;
        this.leadService = leadService;
        this.leadActivityService = leadActivityService;
        this.clinicProfileService = clinicProfileService;
    }

    /**
     * Evaluates one receptionist workflow turn and updates session metadata with workflow context.
     */
    public ReceptionistWorkflowResult evaluate(VoiceSessionEntity session, UUID actorUserId, String userText, String correlationId) {
        ObjectNode metadata = readMetadata(session.getMetadataJson());
        ObjectNode receptionist = metadata.with("receptionist");
        ReceptionistWorkflowState currentState = stateOf(receptionist.path("state").asText(null));
        if (currentState == null) {
            currentState = ReceptionistWorkflowState.GREETING;
        }

        ReceptionistIntent intent = detectIntent(userText);
        receptionist.put("intent", intent.name());
        receptionist.put("lastUserUtterance", userText == null ? "" : userText);
        receptionist.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (receptionist.path("startedAt").asText("").isBlank()) {
            receptionist.put("startedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        }
        receptionist.with("collected");
        receptionist.with("outcome");

        collectFields(receptionist.with("collected"), userText);
        boolean escalate = false;
        String escalationReason = null;
        String deterministicReply = null;
        String promptKey = "AI_RECEPTIONIST_INTENT_DETECTION";
        String llmInput = userText;

        switch (intent) {
            case EMERGENCY_OR_MEDICAL_RISK -> {
                receptionist.put("state", ReceptionistWorkflowState.HUMAN_ESCALATION.name());
                receptionist.with("outcome").put("type", "MEDICAL_RISK_ESCALATION");
                deterministicReply = "I can't provide medical advice. Please contact emergency services or visit nearest emergency care immediately.";
                escalate = true;
                escalationReason = "Emergency or medical risk keyword detected";
                promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
            }
            case HUMAN_CALLBACK -> {
                receptionist.put("state", ReceptionistWorkflowState.HUMAN_ESCALATION.name());
                receptionist.with("outcome").put("type", "HUMAN_CALLBACK_REQUESTED");
                deterministicReply = "I will connect this request to a human receptionist for callback.";
                escalate = true;
                escalationReason = "User requested human callback";
                promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                persistLeadIfPossible(session, receptionist, actorUserId, correlationId, true);
            }
            case CLINIC_FAQ -> {
                receptionist.put("state", ReceptionistWorkflowState.FAQ_RESPONSE.name());
                receptionist.with("outcome").put("type", "FAQ_RESPONSE");
                String faq = faqAnswer(session.getTenantId(), userText);
                if (faq != null) {
                    deterministicReply = faq;
                    promptKey = "AI_RECEPTIONIST_FAQ";
                } else {
                    promptKey = "AI_RECEPTIONIST_FAQ";
                    llmInput = "Answer clinic FAQ safely and concisely for voice. User: " + safe(userText);
                }
            }
            case BOOK_APPOINTMENT -> {
                receptionist.put("state", ReceptionistWorkflowState.COLLECT_APPOINTMENT_DETAILS.name());
                receptionist.with("outcome").put("type", "APPOINTMENT_INTENT");
                receptionist.with("outcome").put("appointmentRequestCreated", true);
                persistLeadIfPossible(session, receptionist, actorUserId, correlationId, false);
                promptKey = "AI_RECEPTIONIST_APPOINTMENT_COLLECTION";
                llmInput = "Collect missing appointment details (name, phone, doctor/specialty, preferred date/time, reason). "
                        + "If uncertain, say receptionist will confirm booking.";
            }
            case LEAD_CAPTURE -> {
                receptionist.put("state", ReceptionistWorkflowState.COLLECT_LEAD_DETAILS.name());
                receptionist.with("outcome").put("type", "LEAD_CAPTURE");
                persistLeadIfPossible(session, receptionist, actorUserId, correlationId, true);
                promptKey = "AI_RECEPTIONIST_LEAD_CAPTURE";
                llmInput = "Collect missing lead details (name, phone, email optional, interest, preferred callback time).";
            }
            case UNKNOWN -> {
                int unknownCount = receptionist.path("unknownCount").asInt(0) + 1;
                receptionist.put("unknownCount", unknownCount);
                receptionist.put("state", ReceptionistWorkflowState.IDENTIFY_INTENT.name());
                receptionist.with("outcome").put("type", "UNKNOWN_INTENT");
                if (unknownCount >= 3) {
                    escalate = true;
                    escalationReason = "Repeated unknown intent";
                    receptionist.put("state", ReceptionistWorkflowState.HUMAN_ESCALATION.name());
                    deterministicReply = "I can connect you to a human receptionist to help with this request.";
                    promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                } else {
                    promptKey = "AI_RECEPTIONIST_INTENT_DETECTION";
                    llmInput = "Ask a concise clarifying question to identify whether the user needs FAQ, appointment booking, or callback.";
                }
            }
        }

        if (currentState == ReceptionistWorkflowState.GREETING && deterministicReply == null) {
            receptionist.put("state", ReceptionistWorkflowState.IDENTIFY_INTENT.name());
            receptionist.with("outcome").put("type", "GREETING");
            promptKey = "AI_RECEPTIONIST_GREETING";
            llmInput = "Greet the user briefly and ask how you can help with clinic information, appointments, or callback.";
        }

        receptionist.with("outcome").put("escalationRequired", escalate);
        if (escalationReason != null) {
            receptionist.with("outcome").put("escalationReason", escalationReason);
        }
        metadata.put("workflowState", receptionist.path("state").asText());
        metadata.put("intent", receptionist.path("intent").asText());
        session.setMetadataJson(writeMetadata(metadata));
        return new ReceptionistWorkflowResult(promptKey, llmInput, deterministicReply, escalate, escalationReason);
    }

    /**
     * Persists the final session summary for receptionist sessions.
     */
    public void applySummary(VoiceSessionEntity session, String summaryText) {
        ObjectNode metadata = readMetadata(session.getMetadataJson());
        ObjectNode receptionist = metadata.with("receptionist");
        receptionist.put("state", ReceptionistWorkflowState.SESSION_SUMMARY.name());
        receptionist.put("summary", summaryText == null ? "" : summaryText);
        receptionist.with("outcome").put("type", receptionist.with("outcome").path("type").asText("SESSION_SUMMARY"));
        metadata.put("workflowState", ReceptionistWorkflowState.SESSION_SUMMARY.name());
        session.setMetadataJson(writeMetadata(metadata));
    }

    /**
     * Marks receptionist workflow lifecycle as completed.
     */
    public void markCompleted(VoiceSessionEntity session) {
        ObjectNode metadata = readMetadata(session.getMetadataJson());
        ObjectNode receptionist = metadata.with("receptionist");
        receptionist.put("state", ReceptionistWorkflowState.COMPLETED.name());
        receptionist.put("completedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        metadata.put("workflowState", ReceptionistWorkflowState.COMPLETED.name());
        session.setMetadataJson(writeMetadata(metadata));
    }

    private void persistLeadIfPossible(VoiceSessionEntity session, ObjectNode receptionist, UUID actorUserId, String correlationId, boolean scheduleFollowUp) {
        ObjectNode collected = receptionist.with("collected");
        String phone = text(collected, "phone");
        String name = text(collected, "name");
        if (phone == null || name == null) {
            return;
        }
        var page = leadService.search(session.getTenantId(), new LeadSearchCriteria(null, null, null, null, phone, false, null, null), 0, 10);
        LeadRecord existing = page.getContent().stream()
                .filter(row -> phone.equals(row.phone()))
                .findFirst()
                .orElse(null);

        String[] parts = splitName(name);
        LeadUpsertCommand command = new LeadUpsertCommand(
                parts[0],
                parts[1],
                phone,
                text(collected, "email"),
                PatientGender.UNKNOWN,
                null,
                LeadSource.AI_RECEPTIONIST,
                "AI_RECEPTIONIST",
                null,
                null,
                LeadStatus.FOLLOW_UP_REQUIRED,
                LeadPriority.MEDIUM,
                "Captured via AI receptionist session " + session.getId(),
                "AI_RECEPTIONIST",
                null,
                scheduleFollowUp ? OffsetDateTime.now(ZoneOffset.UTC).plusHours(2) : null
        );

        LeadRecord saved = existing == null
                ? leadService.create(session.getTenantId(), command, actorUserId)
                : leadService.update(session.getTenantId(), existing.id(), command, actorUserId);
        if (session.getLeadId() == null) {
            session.setLeadId(saved.id());
        }
        receptionist.with("outcome").put("leadCreated", true);
        receptionist.with("outcome").put("leadId", saved.id().toString());
        leadActivityService.record(
                session.getTenantId(),
                saved.id(),
                LeadActivityType.NOTE_ADDED,
                "AI receptionist interaction",
                "Session=" + session.getId() + ", correlation=" + safe(correlationId),
                null,
                null,
                "VOICE_SESSION",
                session.getId(),
                actorUserId
        );
    }

    private ReceptionistIntent detectIntent(String userText) {
        String text = normalize(userText);
        if (containsAny(text, "emergency", "chest pain", "can't breathe", "bleeding", "suicidal")) {
            return ReceptionistIntent.EMERGENCY_OR_MEDICAL_RISK;
        }
        if (containsAny(text, "human", "agent", "receptionist", "callback", "call me")) {
            return ReceptionistIntent.HUMAN_CALLBACK;
        }
        if (containsAny(text, "appointment", "book", "schedule", "slot", "doctor")) {
            return ReceptionistIntent.BOOK_APPOINTMENT;
        }
        if (containsAny(text, "timing", "hours", "open", "address", "location", "service", "consultation fee", "available")) {
            return ReceptionistIntent.CLINIC_FAQ;
        }
        if (containsAny(text, "lead", "enquiry", "inquiry", "interested", "demo", "contact")) {
            return ReceptionistIntent.LEAD_CAPTURE;
        }
        return ReceptionistIntent.UNKNOWN;
    }

    private void collectFields(ObjectNode collected, String userText) {
        if (userText == null) {
            return;
        }
        Matcher phone = PHONE_PATTERN.matcher(userText);
        if (phone.find()) {
            collected.put("phone", phone.group(1).replaceAll("\\s+", ""));
        }
        Matcher email = EMAIL_PATTERN.matcher(userText);
        if (email.find()) {
            collected.put("email", email.group(1));
        }
        Matcher name = NAME_PATTERN.matcher(userText);
        if (name.find()) {
            collected.put("name", name.group(1).trim());
        }
        String normalized = normalize(userText);
        if (containsAny(normalized, "cardio", "skin", "child", "ortho", "ent", "gyn")) {
            collected.put("preferredSpecialty", userText.trim());
        }
        if (containsAny(normalized, "tomorrow", "today", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "am", "pm")) {
            collected.put("preferredDateTime", userText.trim());
        }
    }

    private String faqAnswer(UUID tenantId, String userText) {
        String normalized = normalize(userText);
        var profile = clinicProfileService.findByTenantId(tenantId).orElse(null);
        if (profile == null) {
            return null;
        }
        if (containsAny(normalized, "address", "location")) {
            return "Our clinic address is " + safe(profile.addressLine1()) + (profile.addressLine2() == null ? "" : ", " + profile.addressLine2()) + ".";
        }
        if (containsAny(normalized, "phone", "contact")) {
            return "You can reach the clinic at " + safe(profile.phone()) + ".";
        }
        if (containsAny(normalized, "timing", "hours", "open")) {
            return "You can call us at " + safe(profile.phone()) + " for exact clinic timings.";
        }
        if (containsAny(normalized, "service", "specialty", "doctor")) {
            return "We provide consultation services. For exact doctor availability, I can create an appointment request for receptionist confirmation.";
        }
        return null;
    }

    private ObjectNode readMetadata(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode obj) {
                return obj.deepCopy();
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private String writeMetadata(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private ReceptionistWorkflowState stateOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ReceptionistWorkflowState.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(ObjectNode node, String key) {
        String value = node.path(key).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String[] splitName(String full) {
        String[] parts = full.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], null};
        }
        return new String[]{parts[0], parts[1]};
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
