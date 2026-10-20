package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Reuses the Care AI planner and canonical turn interpreter without portal request state. */
@Service
public class PatientPortalCareAiSharedVoicePlanner {
    private static final String CLARIFICATION = "कृपया डॉक्टर, क्लिनिक, सेवा या उपलब्ध समय के बारे में बताइए।";
    private final PatientPortalCareAiPlanner planner;
    private final PatientPortalCareAiTurnInterpreter interpreter;
    private final SpecialtyResolver specialtyResolver;
    private final Map<String, VoiceState> sessions = new ConcurrentHashMap<>();

    public PatientPortalCareAiSharedVoicePlanner(
            PatientPortalCareAiPlanner planner,
            PatientPortalCareAiTurnInterpreter interpreter
    ) {
        this.planner = planner;
        this.interpreter = interpreter;
        this.specialtyResolver = new SpecialtyResolver(new PatientPortalCareAiEntityRegistry());
    }

    public PatientPortalCareAiVoicePlan plan(
            AivaConversationContext conversation,
            HealthcareAiExecutionContext healthcare,
            String transcript
    ) {
        if (conversation == null || healthcare == null || !StringUtils.hasText(transcript)) {
            return PatientPortalCareAiVoicePlan.clarification(CLARIFICATION, "UNKNOWN");
        }
        VoiceState state = sessions.computeIfAbsent(conversation.sessionId(), ignored -> new VoiceState());
        String message = transcript.trim();
        if (isConversationClose(message)) {
            state.lastIntent = "END_CONVERSATION";
            return PatientPortalCareAiVoicePlan.closing("धन्यवाद। स्वस्थ रहिए।");
        }
        state.recentMessages.add(message);
        if (state.recentMessages.size() > 8) state.recentMessages.remove(0);
        String language = StringUtils.hasText(conversation.language()) ? conversation.language() : "hi-IN";
        PatientPortalCareAiPlanningContext planningContext = planningContext(state, language, message);
        PatientPortalCareAiPlannerDecision decision = null;
        if (planner != null) {
            decision = planner.plan(planningContext, conversation);
        }
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                message, language, planningContext, decision, true, decision != null);
        String intent = turn.intent() == null ? null : PatientPortalCareAiIntent.normalize(turn.intent()).name();
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        boolean genericDoctorDiscovery = isGenericDoctorDiscovery(message);
        boolean utteranceSpeciality = StringUtils.hasText(entities.speciality())
                || StringUtils.hasText(lexicalSpeciality(message));
        boolean explicitSpeciality = utteranceSpeciality
                || (!genericDoctorDiscovery && decision != null && StringUtils.hasText(decision.speciality()));
        if (explicitSpeciality) {
            state.speciality = specialtyResolver.canonicalize(
                    firstNonBlank(entities.speciality(), decision == null ? null : decision.speciality()));
        }
        if (!StringUtils.hasText(state.speciality)) state.speciality = lexicalSpeciality(message);
        if (StringUtils.hasText(entities.date())) state.date = entities.date();
        if (!StringUtils.hasText(intent) || "UNKNOWN".equals(intent)) {
            intent = inferDiscoveryIntent(message, state);
        }
        if (("BOOK_APPOINTMENT".equals(intent) && isPublicDoctorRequest(message))
                || ("BOOK_APPOINTMENT".equals(intent) && "FIND_DOCTOR".equals(state.lastIntent)
                && StringUtils.hasText(lexicalSpeciality(message)) && !containsMutationWord(message))) {
            intent = "FIND_DOCTOR";
        }
        if (isMutation(intent)) {
            state.lastIntent = intent;
            return PatientPortalCareAiVoicePlan.mutation(intent);
        }
        if ("FIND_DOCTOR".equals(intent)) {
            String requestedSpeciality = explicitSpeciality
                    ? specialtyResolver.canonicalize(firstNonBlank(entities.speciality(),
                    decision == null ? null : decision.speciality()))
                    : genericDoctorDiscovery && !isContextualSpecialityFollowUp(message)
                    ? null : specialtyResolver.canonicalize(state.speciality);
            state.lastIntent = intent;
            if (!StringUtils.hasText(requestedSpeciality)
                    && !StringUtils.hasText(entities.doctor())
                    && !StringUtils.hasText(state.doctorId)) {
                return PatientPortalCareAiVoicePlan.clarification("कृपया बताइए, किस विशेषज्ञ डॉक्टर की आवश्यकता है?", intent);
            }
            return new PatientPortalCareAiVoicePlan(intent, "FIND_DOCTOR", message,
                    requestedSpeciality, entities.clinic(), null, entities.location(), null, null, null, false, false);
        }
        if ("FIND_SLOTS".equals(intent)) {
            state.lastIntent = intent;
            if (!StringUtils.hasText(state.doctorId)) {
                return PatientPortalCareAiVoicePlan.clarification("कृपया पहले डॉक्टर का नाम बताइए।", intent);
            }
            LocalDate date = parseDate(entities.date());
            return new PatientPortalCareAiVoicePlan(intent, "FIND_SLOTS", null, null, null, null,
                    null, state.doctorId, date == null ? LocalDate.now().plusDays(1) : date, null, false, false);
        }
        if ("FIND_CLINIC".equals(intent) || "FIND_SERVICE".equals(intent)) {
            state.lastIntent = intent;
            String tool = intent;
            return new PatientPortalCareAiVoicePlan(intent, tool, message, null, entities.clinic(),
                    entities.service(), entities.location(), null, null, null, false, false);
        }
        state.lastIntent = "UNKNOWN";
        return PatientPortalCareAiVoicePlan.clarification(CLARIFICATION, "UNKNOWN");
    }

    public void rememberDoctor(String sessionId, String doctorId) {
        if (StringUtils.hasText(sessionId) && StringUtils.hasText(doctorId)) {
            sessions.computeIfAbsent(sessionId, ignored -> new VoiceState()).doctorId = doctorId;
        }
    }

    public void endSession(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    private PatientPortalCareAiPlanningContext planningContext(VoiceState state, String language, String message) {
        return new PatientPortalCareAiPlanningContext(language, message, state.lastIntent, null, null, false,
                null, null, null, state.speciality, null, state.date, null, null,
                List.of(), List.of("FIND_DOCTOR", "FIND_CLINIC", "FIND_SERVICE", "FIND_SLOTS"),
                List.of(), List.of(), List.of(), List.of(), state.recentMessages, null, 0);
    }

    private String inferDiscoveryIntent(String message, VoiceState state) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("slot") || value.contains("available") || value.contains("उपलब्ध") || value.contains("समय")) {
            return "FIND_SLOTS";
        }
        if (value.contains("clinic") || value.contains("क्लिनिक")) return "FIND_CLINIC";
        if (value.contains("service") || value.contains("सेवा")) return "FIND_SERVICE";
        if (value.contains("doctor") || value.contains("डॉक्टर") || value.contains("विशेषज्ञ")
                || StringUtils.hasText(state.speciality) || "FIND_DOCTOR".equals(state.lastIntent)) return "FIND_DOCTOR";
        return null;
    }

    private boolean isConversationClose(String message) {
        String value = message == null ? "" : message.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[.!?]+$", "").trim();
        return value.equals("thanks") || value.equals("thank you") || value.equals("thankyou")
                || value.equals("thx") || value.equals("bye") || value.equals("goodbye")
                || value.equals("बस इतना ही") || value.equals("ठीक है धन्यवाद")
                || value.equals("धन्यवाद") || value.equals("थैंक यू") || value.equals("अलविदा");
    }

    private boolean isGenericDoctorDiscovery(String message) {
        String value = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        boolean doctor = value.contains("doctor") || value.contains("डॉक्टर") || value.contains("विशेषज्ञ");
        boolean availability = value.contains("available") || value.contains("अवेलेबल")
                || value.contains("उपलब्ध") || value.contains("कौन से") || value.contains("which");
        return doctor && availability && !StringUtils.hasText(lexicalSpeciality(message));
    }

    private boolean isContextualSpecialityFollowUp(String message) {
        String value = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("उस specialty") || value.contains("उसी specialty")
                || value.contains("उस विशेषज्ञता") || value.contains("उसी में")
                || value.contains("और कौन") || value.contains("same specialty")
                || value.contains("that specialty");
    }

    private String lexicalSpeciality(String message) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("cardiologist") || value.contains("cardiology") || value.contains("कार्डियोलॉजिस्ट")) {
            return value.contains("कार्डियोलॉजिस्ट") ? "कार्डियोलॉजी" : "Cardiology";
        }
        if (value.contains("general physician") || value.contains("general physican")
                || value.contains("general position") || value.contains("जनरल फिजिशियन")) {
            return "General Medicine";
        }
        return null;
    }

    private boolean isMutation(String intent) {
        return "BOOK_APPOINTMENT".equals(intent) || "CANCEL_APPOINTMENT".equals(intent)
                || "RESCHEDULE_APPOINTMENT".equals(intent) || "CHECK_APPOINTMENT".equals(intent);
    }

    private boolean isPublicDoctorRequest(String message) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        boolean doctor = value.contains("doctor") || value.contains("डॉक्टर") || value.contains("विशेषज्ञ");
        boolean mutation = value.contains("book") || value.contains("बुक") || value.contains("appointment")
                || value.contains("अपॉइंटमेंट") || value.contains("cancel") || value.contains("रद्द")
                || value.contains("reschedule");
        return doctor && !mutation;
    }

    private boolean containsMutationWord(String message) {
        String value = message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("book") || value.contains("बुक") || value.contains("appointment")
                || value.contains("अपॉइंटमेंट") || value.contains("cancel") || value.contains("रद्द")
                || value.contains("reschedule");
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException ignored) { return null; }
    }

    private static final class VoiceState {
        private String doctorId;
        private String speciality;
        private String date;
        private String lastIntent;
        private final List<String> recentMessages = new ArrayList<>();
    }
}
