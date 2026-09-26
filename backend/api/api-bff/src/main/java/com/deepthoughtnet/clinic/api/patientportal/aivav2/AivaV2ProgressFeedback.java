package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.util.Locale;

/** Deterministic, locale-aware copy for a single in-flight authoritative operation. */
public final class AivaV2ProgressFeedback {
    private AivaV2ProgressFeedback() { }

    public static AivaV2ProgressEvent generic(String language, ResponseStyle style) {
        String locale = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        boolean hindi = locale.startsWith("hi");
        boolean hinglish = hindi && style == ResponseStyle.HINGLISH;
        return new AivaV2ProgressEvent("SEMANTIC_ROUTING", hindi
                ? (hinglish ? "Ek pal, main aapki request check kar rahi hoon." : "एक क्षण, मैं आपकी रिक्वेस्ट देख रही हूँ।")
                : "One moment while I check.");
    }

    public static AivaV2ProgressEvent forOperation(Operation operation, String language, ResponseStyle style) {
        if (operation == null || operation == Operation.INTRO || operation == Operation.UNKNOWN
                || operation == Operation.ANSWER_CONTEXT || operation == Operation.END_CONVERSATION
                || operation == Operation.SUSPEND_BOOKING || operation == Operation.RESUME_BOOKING
                || operation == Operation.ABANDON_BOOKING || operation == Operation.REQUEST_HUMAN_ASSISTANCE
                || operation == Operation.PRESENCE_CHECK || operation == Operation.REPEAT_LAST_RESPONSE
                || operation == Operation.HOLD || operation == Operation.CONTINUE_CONVERSATION) return null;
        String locale = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        boolean hindi = locale.startsWith("hi");
        boolean hinglish = hindi && style == ResponseStyle.HINGLISH;
        String copy = switch (operation) {
            case START_BOOKING, RESOLVE_PROVIDER -> hindi
                    ? (hinglish ? "Ek pal, main available doctors dekh rahi hoon." : "एक क्षण, मैं उपलब्ध डॉक्टर देख रही हूँ।")
                    : "Let me check available doctors.";
            case GET_AVAILABILITY, UPDATE_BOOKING, FIND_NEXT_AVAILABLE_SLOT -> hindi
                    ? (hinglish ? "Main available slots dekh rahi hoon." : "मैं उपलब्ध स्लॉट देख रही हूँ।")
                    : "I’m checking available slots.";
            case LOOKUP_APPOINTMENTS, NEXT_UPCOMING_APPOINTMENT -> hindi
                    ? (hinglish ? "Main aapki appointment ki jaankari dekh rahi hoon." : "मैं आपकी अपॉइंटमेंट की जानकारी देख रही हूँ।")
                    : "Let me check your appointments.";
            case PREPARE_BOOKING, CONFIRM_BOOKING -> hindi
                    ? (hinglish ? "Main appointment ki details confirm kar rahi hoon." : "मैं अपॉइंटमेंट की जानकारी पक्की कर रही हूँ।")
                    : "Let me confirm those appointment details.";
            case CANCEL_APPOINTMENT, PREPARE_CANCELLATION, CONFIRM_CANCELLATION -> hindi
                    ? (hinglish ? "Main us appointment ki jaankari dekh rahi hoon." : "मैं उस अपॉइंटमेंट की जानकारी देख रही हूँ।")
                    : "Let me check that appointment.";
            case RESCHEDULE_APPOINTMENT, UPDATE_RESCHEDULE, GET_RESCHEDULE_AVAILABILITY,
                    SELECT_RESCHEDULE_SLOT, PREPARE_RESCHEDULE, CONFIRM_RESCHEDULE -> hindi
                    ? (hinglish ? "Main nayi date ke liye available time dekh rahi hoon." : "मैं नई तारीख के लिए उपलब्ध समय देख रही हूँ।")
                    : "Let me check available times for the new date.";
            default -> hindi
                    ? (hinglish ? "Ek pal, main aapki request check kar rahi hoon." : "एक क्षण, मैं आपकी रिक्वेस्ट देख रही हूँ।")
                    : "One moment while I check.";
        };
        return new AivaV2ProgressEvent(operation.name(), copy);
    }
}
