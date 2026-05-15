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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Controlled v2 AI receptionist workflow service with slot filling and escalation routing.
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
     * Evaluates one receptionist workflow turn and updates session metadata with v2 memory.
     */
    public ReceptionistWorkflowResult evaluate(VoiceSessionEntity session, UUID actorUserId, String userText, String correlationId) {
        ObjectNode metadata = readMetadata(session.getMetadataJson());
        ObjectNode receptionist = metadata.with("receptionist");
        ObjectNode memory = receptionist.with("memory");
        ObjectNode slots = receptionist.with("slots");
        ObjectNode escalation = receptionist.with("escalation");
        ObjectNode outcome = receptionist.with("outcome");

        ReceptionistWorkflowState state = stateOf(receptionist.path("state").asText(null));
        if (state == null) {
            state = ReceptionistWorkflowState.GREETING;
        }

        String normalized = normalize(userText);
        ReceptionistIntent intent = detectIntent(normalized);
        double confidence = confidence(intent, normalized);

        collectSlots(slots, userText, normalized);
        receptionist.put("intent", intent.name());
        receptionist.put("intentConfidence", confidence);
        receptionist.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (receptionist.path("startedAt").asText("").isBlank()) {
            receptionist.put("startedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        }

        memory.put("lastIntent", intent.name());
        memory.put("userClarification", safe(userText));
        memory.put("escalationState", escalation.path("status").asText("NONE"));

        String promptKey = "AI_RECEPTIONIST_INTENT_DETECTION";
        String llmInput = userText;
        String deterministicReply = null;
        boolean escalate = false;
        String escalationReason = null;
        String escalationCategory = null;
        String escalationPriority = null;

        if (intent == ReceptionistIntent.EMERGENCY_OR_MEDICAL_RISK) {
            escalate = true;
            escalationReason = "Medical risk keywords detected";
            escalationCategory = "EMERGENCY_GUIDANCE";
            escalationPriority = "CRITICAL";
            deterministicReply = "I can't provide medical advice. Please contact emergency services or visit nearest emergency care immediately.";
            state = ReceptionistWorkflowState.HUMAN_ESCALATION;
            promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
            slots.put("urgencyRiskFlag", true);
        } else if (intent == ReceptionistIntent.HUMAN_CALLBACK) {
            escalate = true;
            escalationReason = "User requested human support";
            escalationCategory = "RECEPTIONIST";
            escalationPriority = "HIGH";
            deterministicReply = "I will route this to our receptionist team for a callback.";
            state = ReceptionistWorkflowState.HUMAN_ESCALATION;
            promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
        } else if (confidence < 0.45d) {
            state = ReceptionistWorkflowState.IDENTIFY_INTENT;
            promptKey = "AI_RECEPTIONIST_INTENT_DETECTION";
            llmInput = "Ask one concise clarifying question to identify user intent. Offer human support if unclear.";
            memory.put("lastAiQuestion", "Could you share if this is about appointment booking, billing, insurance, clinic details, or callback?");
            if (receptionist.path("unknownCount").asInt(0) >= 2) {
                escalate = true;
                escalationReason = "Low confidence across repeated turns";
                escalationCategory = "RECEPTIONIST";
                escalationPriority = "MEDIUM";
            }
            receptionist.put("unknownCount", receptionist.path("unknownCount").asInt(0) + 1);
        } else {
            switch (intent) {
                case CLINIC_FAQ -> {
                    state = ReceptionistWorkflowState.FAQ_RESPONSE;
                    String faq = faqAnswer(session.getTenantId(), normalized);
                    promptKey = "AI_RECEPTIONIST_FAQ";
                    if (faq != null) {
                        deterministicReply = faq;
                    } else {
                        llmInput = "Answer clinic FAQ safely and concisely for voice. Do not provide diagnosis or treatment advice.";
                    }
                }
                case BOOK_APPOINTMENT -> {
                    state = ReceptionistWorkflowState.COLLECT_APPOINTMENT_DETAILS;
                    promptKey = "AI_RECEPTIONIST_APPOINTMENT_COLLECTION";
                    outcome.put("appointmentIntent", true);
                    handleAppointmentFlow(slots, outcome, memory, normalized);
                    llmInput = appointmentCollectionPrompt(slots, memory);
                    if (hasNameAndPhone(slots)) {
                        persistLeadIfPossible(session, receptionist, actorUserId, correlationId, false);
                    }
                }
                case RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT -> {
                    state = ReceptionistWorkflowState.HUMAN_ESCALATION;
                    escalate = true;
                    escalationReason = "Appointment change requires staff verification";
                    escalationCategory = "RECEPTIONIST";
                    escalationPriority = "MEDIUM";
                    deterministicReply = "I will route this " + intent.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                            + " request to our receptionist team for confirmation.";
                    promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                }
                case LEAD_CAPTURE -> {
                    state = ReceptionistWorkflowState.COLLECT_LEAD_DETAILS;
                    promptKey = "AI_RECEPTIONIST_LEAD_CAPTURE";
                    llmInput = "Collect missing lead details: name, phone, email optional, interest, preferred callback time.";
                    if (hasNameAndPhone(slots)) {
                        persistLeadIfPossible(session, receptionist, actorUserId, correlationId, true);
                    }
                }
                case BILLING_QUERY -> {
                    state = ReceptionistWorkflowState.HUMAN_ESCALATION;
                    escalate = true;
                    escalationReason = "Billing desk route";
                    escalationCategory = "BILLING_DESK";
                    escalationPriority = "MEDIUM";
                    deterministicReply = "I will route this to our billing desk and arrange a callback.";
                    promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                }
                case INSURANCE_QUERY -> {
                    state = ReceptionistWorkflowState.HUMAN_ESCALATION;
                    escalate = true;
                    escalationReason = "Insurance support route";
                    escalationCategory = "CLINIC_ADMIN";
                    escalationPriority = "MEDIUM";
                    deterministicReply = "I will route this insurance query to our clinic admin team for follow-up.";
                    promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                }
                case COMPLAINT -> {
                    state = ReceptionistWorkflowState.HUMAN_ESCALATION;
                    escalate = true;
                    escalationReason = "Complaint handling route";
                    escalationCategory = "CLINIC_ADMIN";
                    escalationPriority = "HIGH";
                    deterministicReply = "I am escalating this complaint to clinic administration for priority follow-up.";
                    promptKey = "AI_RECEPTIONIST_SAFE_ESCALATION";
                }
                case UNKNOWN -> {
                    state = ReceptionistWorkflowState.IDENTIFY_INTENT;
                    promptKey = "AI_RECEPTIONIST_INTENT_DETECTION";
                    llmInput = "Ask a concise clarifying question to identify user intent.";
                    receptionist.put("unknownCount", receptionist.path("unknownCount").asInt(0) + 1);
                }
                case HUMAN_CALLBACK, EMERGENCY_OR_MEDICAL_RISK -> {
                    // handled above
                }
            }
        }

        if (state == ReceptionistWorkflowState.GREETING && deterministicReply == null) {
            promptKey = "AI_RECEPTIONIST_GREETING";
            llmInput = "Greet briefly and ask if user needs clinic FAQ, appointment support, billing, insurance, or callback.";
            state = ReceptionistWorkflowState.IDENTIFY_INTENT;
        }

        receptionist.put("state", state.name());
        memory.put("currentWorkflowState", state.name());
        memory.set("missingFields", missingFields(slots, intent));

        if (escalate) {
            escalation.put("category", safe(escalationCategory));
            escalation.put("reason", safe(escalationReason));
            escalation.put("priority", safe(escalationPriority));
            escalation.put("status", "OPEN");
            escalation.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
            outcome.put("escalationRequired", true);
            outcome.put("escalationReason", safe(escalationReason));
        }

        metadata.put("workflowState", state.name());
        metadata.put("intent", intent.name());
        session.setMetadataJson(writeMetadata(metadata));

        return new ReceptionistWorkflowResult(
                intent,
                confidence,
                promptKey,
                llmInput,
                deterministicReply,
                escalate,
                escalationReason,
                escalationCategory,
                escalationPriority,
                extractionSummary(intent, confidence, slots, memory)
        );
    }

    /**
     * Persists the final session summary for receptionist sessions.
     */
    public void applySummary(VoiceSessionEntity session, String summaryText) {
        ObjectNode metadata = readMetadata(session.getMetadataJson());
        ObjectNode receptionist = metadata.with("receptionist");
        receptionist.put("state", ReceptionistWorkflowState.SESSION_SUMMARY.name());
        receptionist.put("summary", summaryText == null ? "" : summaryText);
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

    private void handleAppointmentFlow(ObjectNode slots, ObjectNode outcome, ObjectNode memory, String normalized) {
        List<String> missing = requiredAppointmentMissing(slots);
        memory.set("missingFields", toArray(missing));
        outcome.put("appointmentRequestCreated", true);
        if (missing.isEmpty()) {
            outcome.put("availabilityLookupReady", true);
            outcome.put("bookingConfirmationRequired", true);
            ArrayNode suggested = outcome.putArray("suggestedSlots");
            String date = text(slots, "preferredDate");
            String time = text(slots, "preferredTime");
            if (date != null && time != null) {
                suggested.add(date + " " + time);
            }
            if (containsAny(normalized, "confirm", "yes, book", "go ahead")) {
                outcome.put("bookingRequestStatus", "CONFIRMED_FOR_STAFF");
            } else {
                outcome.put("bookingRequestStatus", "PENDING_CONFIRMATION");
            }
        }
    }

    private String appointmentCollectionPrompt(ObjectNode slots, ObjectNode memory) {
        List<String> missing = requiredAppointmentMissing(slots);
        if (missing.isEmpty()) {
            memory.put("lastAiQuestion", "Please confirm if you want us to proceed with this booking request.");
            return "Ask for explicit confirmation before creating booking request. If no confirmation, hold as pending.";
        }
        memory.put("lastAiQuestion", "Please share: " + String.join(", ", missing));
        return "Collect missing appointment fields only: " + String.join(", ", missing) + ". Be concise.";
    }

    private List<String> requiredAppointmentMissing(ObjectNode slots) {
        List<String> missing = new ArrayList<>();
        if (text(slots, "name") == null) missing.add("name");
        if (text(slots, "phone") == null) missing.add("phone");
        if (text(slots, "preferredDate") == null) missing.add("preferred date");
        if (text(slots, "preferredTime") == null) missing.add("preferred time");
        if (text(slots, "reasonForVisit") == null) missing.add("reason for visit");
        if (text(slots, "preferredDoctor") == null && text(slots, "specialty") == null) missing.add("preferred doctor or specialty");
        return missing;
    }

    private boolean hasNameAndPhone(ObjectNode slots) {
        return text(slots, "name") != null && text(slots, "phone") != null;
    }

    private void collectSlots(ObjectNode slots, String userText, String normalized) {
        if (userText == null || userText.isBlank()) {
            return;
        }
        Matcher phone = PHONE_PATTERN.matcher(userText);
        if (phone.find()) {
            slots.put("phone", phone.group(1).replaceAll("\\s+", ""));
        }
        Matcher email = EMAIL_PATTERN.matcher(userText);
        if (email.find()) {
            slots.put("email", email.group(1));
        }
        Matcher name = NAME_PATTERN.matcher(userText);
        if (name.find()) {
            slots.put("name", name.group(1).trim());
        }
        if (containsAny(normalized, "callback", "call back", "call me")) {
            slots.put("callbackPreference", "CALLBACK_REQUESTED");
        }
        if (containsAny(normalized, "insurance", "policy", "claim")) {
            slots.put("reasonForVisit", "INSURANCE_QUERY");
        }
        if (containsAny(normalized, "billing", "invoice", "payment")) {
            slots.put("reasonForVisit", "BILLING_QUERY");
        }
        if (containsAny(normalized, "chest pain", "can't breathe", "bleeding", "suicidal")) {
            slots.put("urgencyRiskFlag", true);
        }
        if (containsAny(normalized, "cardio", "derma", "skin", "child", "pediatric", "ortho", "ent", "gyn")) {
            slots.put("specialty", userText.trim());
        }
        if (containsAny(normalized, "dr ", "doctor ")) {
            slots.put("preferredDoctor", userText.trim());
        }
        if (containsAny(normalized, "today", "tomorrow", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) {
            slots.put("preferredDate", userText.trim());
        }
        if (containsAny(normalized, "am", "pm", "morning", "afternoon", "evening")) {
            slots.put("preferredTime", userText.trim());
        }
        if (containsAny(normalized, "for", "because", "reason")) {
            slots.put("reasonForVisit", userText.trim());
        }
    }

    private ReceptionistIntent detectIntent(String text) {
        if (containsAny(text, "emergency", "chest pain", "can't breathe", "bleeding", "suicidal", "unconscious")) {
            return ReceptionistIntent.EMERGENCY_OR_MEDICAL_RISK;
        }
        if (containsAny(text, "complaint", "bad experience", "report issue")) {
            return ReceptionistIntent.COMPLAINT;
        }
        if (containsAny(text, "insurance", "claim", "policy")) {
            return ReceptionistIntent.INSURANCE_QUERY;
        }
        if (containsAny(text, "billing", "invoice", "payment", "refund")) {
            return ReceptionistIntent.BILLING_QUERY;
        }
        if (containsAny(text, "reschedule", "move appointment", "change appointment")) {
            return ReceptionistIntent.RESCHEDULE_APPOINTMENT;
        }
        if (containsAny(text, "cancel appointment", "cancel my appointment")) {
            return ReceptionistIntent.CANCEL_APPOINTMENT;
        }
        if (containsAny(text, "human", "agent", "receptionist", "callback", "call me")) {
            return ReceptionistIntent.HUMAN_CALLBACK;
        }
        if (containsAny(text, "appointment", "book", "schedule", "slot", "doctor")) {
            return ReceptionistIntent.BOOK_APPOINTMENT;
        }
        if (containsAny(text, "timing", "hours", "open", "address", "location", "service", "consultation")) {
            return ReceptionistIntent.CLINIC_FAQ;
        }
        if (containsAny(text, "enquiry", "inquiry", "interested", "demo", "contact")) {
            return ReceptionistIntent.LEAD_CAPTURE;
        }
        return ReceptionistIntent.UNKNOWN;
    }

    private double confidence(ReceptionistIntent intent, String text) {
        return switch (intent) {
            case EMERGENCY_OR_MEDICAL_RISK -> 0.99d;
            case BOOK_APPOINTMENT, CLINIC_FAQ -> containsAny(text, "appointment", "book", "timing", "address") ? 0.9d : 0.72d;
            case BILLING_QUERY, INSURANCE_QUERY, COMPLAINT -> 0.85d;
            case RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT, HUMAN_CALLBACK -> 0.88d;
            case LEAD_CAPTURE -> 0.78d;
            case UNKNOWN -> 0.28d;
        };
    }

    private String faqAnswer(UUID tenantId, String normalized) {
        var profile = clinicProfileService.findByTenantId(tenantId).orElse(null);
        if (profile == null) {
            return null;
        }
        if (containsAny(normalized, "address", "location")) {
            return "Our clinic address is " + safe(profile.addressLine1())
                    + (profile.addressLine2() == null ? "" : ", " + profile.addressLine2()) + ".";
        }
        if (containsAny(normalized, "phone", "contact")) {
            return "You can reach the clinic at " + safe(profile.phone()) + ".";
        }
        if (containsAny(normalized, "timing", "hours", "open")) {
            return "Please call " + safe(profile.phone()) + " for today’s exact clinic timings.";
        }
        if (containsAny(normalized, "service", "specialty", "doctor")) {
            return "We offer consultation services across specialties. I can collect details and route an appointment request.";
        }
        return null;
    }

    private String extractionSummary(ReceptionistIntent intent, double confidence, ObjectNode slots, ObjectNode memory) {
        return "intent=" + intent.name()
                + ", confidence=" + String.format(Locale.ROOT, "%.2f", confidence)
                + ", missing=" + memory.path("missingFields").toString()
                + ", slots=" + slots.toString();
    }

    private ArrayNode missingFields(ObjectNode slots, ReceptionistIntent intent) {
        return switch (intent) {
            case BOOK_APPOINTMENT -> toArray(requiredAppointmentMissing(slots));
            case LEAD_CAPTURE -> {
                List<String> missing = new ArrayList<>();
                if (text(slots, "name") == null) missing.add("name");
                if (text(slots, "phone") == null) missing.add("phone");
                yield toArray(missing);
            }
            default -> objectMapper.createArrayNode();
        };
    }

    private ArrayNode toArray(List<String> items) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String item : items) {
            array.add(item);
        }
        return array;
    }

    private void persistLeadIfPossible(VoiceSessionEntity session, ObjectNode receptionist, UUID actorUserId, String correlationId, boolean scheduleFollowUp) {
        ObjectNode slots = receptionist.with("slots");
        String phone = text(slots, "phone");
        String name = text(slots, "name");
        if (phone == null || name == null) {
            return;
        }
        var page = leadService.search(session.getTenantId(), new LeadSearchCriteria(null, null, null, null, phone, false, null, null), 0, 10);
        LeadRecord existing = page.getContent().stream().filter(row -> phone.equals(row.phone())).findFirst().orElse(null);

        String[] parts = splitName(name);
        LeadUpsertCommand command = new LeadUpsertCommand(
                parts[0],
                parts[1],
                phone,
                text(slots, "email"),
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
        return parts.length == 1 ? new String[]{parts[0], null} : new String[]{parts[0], parts[1]};
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
