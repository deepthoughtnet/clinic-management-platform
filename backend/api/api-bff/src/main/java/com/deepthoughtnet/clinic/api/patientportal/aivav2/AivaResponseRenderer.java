package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaStructuredResponse.*;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.InteractiveAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Renders only typed AIVA facts. Legacy text is returned solely for unmigrated LEGACY responses. */
@Component
public class AivaResponseRenderer {
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter HI_DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("hi-IN"));

    public MessageResponse render(MessageResponse response, String responseLanguage, ResponseStyle responseStyle) {
        if (response == null) return null;
        AivaStructuredResponse structured = response.structuredResponse();
        if (structured == null || structured.type() == ResponseType.LEGACY) return response;
        String language = responseLanguage == null ? "en" : responseLanguage.toLowerCase(Locale.ROOT);
        boolean hindi = language.startsWith("hi");
        boolean hinglish = hindi && responseStyle == ResponseStyle.HINGLISH;
        String message = renderPayload(structured, hinglish, hindi);
        if (message == null) message = renderPayload(structured, false, false);
        if (message == null) message = response.assistantMessage();
        return new MessageResponse(response.conversationId(), response.turnId(), message, response.responseCategory(),
                response.state(), response.interpretationProvider(), response.fallbackUsed(), response.actions(), structured);
    }

    private String renderPayload(AivaStructuredResponse response, boolean hinglish, boolean hindi) {
        Payload payload = response.payload();
        return switch (response.type()) {
            case NEED_PROVIDER -> needProvider((NeedProviderPayload) payload, hinglish, hindi);
            case PROVIDER_CHOICES -> providerChoices((ProviderChoicesPayload) payload, hinglish, hindi);
            case PROVIDER_NOT_FOUND -> hindi ? (hinglish ? "Is doctor se match nahi mila." : "इस डॉक्टर से मेल नहीं मिला।")
                    : "I could not find a matching doctor.";
            case NEED_DATE -> hindi ? (hinglish ? "Aap kis date ki appointment lena chahenge?"
                    : "आप किस तारीख की अपॉइंटमेंट लेना चाहेंगे?") : "What date would you prefer?";
            case AVAILABLE_SLOTS -> availability((AvailabilityPayload) payload, hinglish, hindi);
            case RESCHEDULE_AVAILABLE_SLOTS -> rescheduleAvailability((AvailabilityPayload) payload, hinglish, hindi);
            case NO_MORE_SLOTS -> payload instanceof NoMoreSlotsPayload value
                    ? noMore(value, hinglish, hindi) : hindi ? "अभी और उपलब्ध स्लॉट नहीं हैं।" : "There are no more available slots.";
            case RESCHEDULE_NO_MORE_SLOTS -> payload instanceof NoMoreSlotsPayload value
                    ? rescheduleNoMore(value, hinglish, hindi) : hindi ? "इस रीशेड्यूल के लिए और स्लॉट उपलब्ध नहीं हैं।" : "There are no more matching reschedule slots.";
            case BOOKING_CONFIRMATION -> bookingConfirmation((BookingConfirmationPayload) payload, hinglish, hindi);
            case BOOKING_SUCCESS -> hindi ? (hinglish ? "Aapki appointment book ho gayi hai."
                    : "आपकी अपॉइंटमेंट बुक हो गई है।") : "Your appointment is booked.";
            case APPOINTMENTS_NONE -> noAppointments((AppointmentListPayload) payload, hinglish, hindi);
            case APPOINTMENTS_FOUND -> appointments((AppointmentListPayload) payload, hinglish, hindi);
            case LOOKUP_CLARIFICATION -> hindi ? (hinglish ? "Kaunsi appointment ke baare mein jaanna chahte hain?"
                    : "आप किस अपॉइंटमेंट के बारे में जानना चाहते हैं?")
                    : "Are you asking about an appointment with a specific doctor or specialty?";
            case CANCELLATION_NONE -> hindi ? (hinglish ? "Cancel karne ke liye koi upcoming appointment nahi mili."
                    : "रद्द करने के लिए कोई आगामी अपॉइंटमेंट नहीं मिली।") : "I couldn't find an upcoming appointment to cancel.";
            case CANCELLATION_CHOICES -> appointmentChoices(((CancellationChoicesPayload) payload).candidates(), "cancel", hinglish, hindi);
            case CANCELLATION_CONFIRMATION -> cancellationConfirmation(
                    ((CancellationConfirmationPayload) payload).appointment(), hinglish, hindi);
            case CANCELLATION_REJECTED -> hindi ? (hinglish ? "Theek hai, main appointment cancel nahi karunga."
                    : "ठीक है, यह अपॉइंटमेंट रद्द नहीं की जाएगी।") : "I’ll leave that appointment unchanged.";
            case CANCELLATION_SUCCESS -> cancellationSuccess(((CancellationSuccessPayload) payload).appointment(), hinglish, hindi);
            case RESCHEDULE_SOURCE_NONE -> hindi ? (hinglish ? "Reschedule karne ke liye koi upcoming appointment nahi mili."
                    : "रीशेड्यूल करने के लिए कोई आगामी अपॉइंटमेंट नहीं मिली।") : "I couldn't find an upcoming appointment to reschedule.";
            case RESCHEDULE_SOURCE_CHOICES -> appointmentChoices(((RescheduleSourceChoicesPayload) payload).candidates(), "reschedule", hinglish, hindi);
            case RESCHEDULE_NEED_DATE -> rescheduleNeedDate((RescheduleNeedDatePayload) payload, hinglish, hindi);
            case RESCHEDULE_CONFIRMATION -> rescheduleConfirmation((RescheduleConfirmationPayload) payload, hinglish, hindi);
            case RESCHEDULE_REJECTED -> hindi ? (hinglish ? "Theek hai, purani appointment waise hi rahegi. Aap koi aur slot ya date chun sakte hain."
                    : "ठीक है, आपकी मौजूदा अपॉइंटमेंट वैसी ही रहेगी। आप कोई दूसरा समय या तारीख चुन सकते हैं।")
                    : "I will leave your original appointment unchanged. You can choose another slot or date.";
            case RESCHEDULE_SUCCESS -> rescheduleSuccess((RescheduleSuccessPayload) payload, hinglish, hindi);
            case STALE_RESULT -> hindi ? (hinglish ? "Yeh result ab purana ho gaya hai. Dobara koshish karein."
                    : "यह परिणाम अब उपलब्ध नहीं है। कृपया दोबारा कोशिश करें।") : "That result is no longer current. Please try again.";
            case FAILURE -> hindi ? (hinglish ? "Appointment request abhi poori nahi ho saki."
                    : "अपॉइंटमेंट अनुरोध अभी पूरा नहीं हो सका।") : "Appointment information could not be retrieved.";
            case CLARIFICATION -> clarification((ClarificationPayload) payload, hinglish, hindi);
            case CALL_TO_BOOK -> callToBook((CallToBookPayload) payload, hinglish, hindi);
            case LEGACY -> null;
        };
    }

    private String needProvider(NeedProviderPayload payload, boolean hinglish, boolean hindi) {
        if (hindi) return hinglish ? "Aap kis doctor ya specialty ke saath appointment lena chahenge?"
                : "आप किस डॉक्टर या विशेषज्ञ के साथ अपॉइंटमेंट लेना चाहेंगे?";
        return "Which doctor or specialty would you like?";
    }

    private String providerChoices(ProviderChoicesPayload payload, boolean hinglish, boolean hindi) {
        String choices = choices(payload.candidates().stream().map(ProviderOption::doctorDisplayName).toList());
        if (hindi) return hinglish ? "Mujhe ye doctors mile: " + choices + ". Aap kise chunenge?"
                : "मुझे ये डॉक्टर मिले: " + choices + "। आप किसे चुनेंगे?";
        return "I found these doctors: " + choices + ". Which one would you like?";
    }

    private String availability(AvailabilityPayload payload, boolean hinglish, boolean hindi) {
        String date = formatDate(payload.date(), hinglish, hindi);
        if (payload.slots().isEmpty()) {
            if (hindi) return hinglish ? date + " ke liye koi available slots nahi hain. Kya aap doosri date dekhna chahenge?"
                    : date + " के लिए कोई स्लॉट उपलब्ध नहीं है। क्या आप दूसरी तारीख देखना चाहेंगे?";
            return "There are no available slots for " + date + ". Would you like to check another date?";
        }
        String slots = slotLabels(payload.slots());
        if (hindi) return hinglish ? "Available slots for " + date + ": " + slots + ". Aapke liye kaunsa time theek rahega?"
                : date + " के लिए उपलब्ध स्लॉट: " + slots + "। आपके लिए कौन-सा समय ठीक रहेगा?";
        return "Available slots for " + date + ": " + slots + ". Which one works?";
    }

    private String rescheduleAvailability(AvailabilityPayload payload, boolean hinglish, boolean hindi) {
        String date = formatDate(payload.date(), hinglish, hindi);
        if (payload.slots().isEmpty()) {
            if (hindi) return hinglish ? date + " ke liye reschedule ke koi available slots nahi hain. Kya doosri date dekhein?"
                    : date + " को रीशेड्यूल के लिए कोई स्लॉट उपलब्ध नहीं है। क्या आप दूसरी तारीख देखना चाहेंगे?";
            return "There are no available reschedule slots for " + date + ". Would you like to check another date?";
        }
        String slots = slotLabels(payload.slots());
        if (hindi) return hinglish ? "Reschedule ke liye " + date + " par available slots: " + slots + ". Kaunsa time theek rahega?"
                : "रीशेड्यूल के लिए " + date + " को उपलब्ध स्लॉट: " + slots + "। कौन-सा समय ठीक रहेगा?";
        return "Available reschedule slots for " + date + ": " + slots + ". Which one works?";
    }

    private String noMore(NoMoreSlotsPayload payload, boolean hinglish, boolean hindi) {
        String provider = safe(payload.providerDisplayName(), hindi ? "डॉक्टर" : "Doctor");
        String date = formatDate(payload.date(), hinglish, hindi);
        if (hindi) return hinglish ? provider + " ke liye " + date + " par aur slots nahi hain. Doosri date ya time dekhein?"
                : date + " को " + provider + " के लिए और स्लॉट उपलब्ध नहीं हैं। क्या आप दूसरी तारीख या समय देखना चाहेंगे?";
        return provider + " has no more available slots on " + date + ". Would you like to check another date or time?";
    }

    private String rescheduleNoMore(NoMoreSlotsPayload payload, boolean hinglish, boolean hindi) {
        if (hindi) return hinglish ? "Is reschedule ke liye aur slots nahi hain. Doosri date ya time dekhein?"
                : "इस रीशेड्यूल के लिए और स्लॉट उपलब्ध नहीं हैं। क्या आप दूसरी तारीख या समय देखना चाहेंगे?";
        return "There are no more matching reschedule slots.";
    }

    private String bookingConfirmation(BookingConfirmationPayload payload, boolean hinglish, boolean hindi) {
        String provider = safe(payload.providerDisplayName(), hindi ? "डॉक्टर" : "Doctor");
        String date = formatDate(payload.date(), hinglish, hindi);
        String time = formatTime(payload.time());
        if (hindi) return hinglish ? provider + " ke saath " + date + " ko " + time + " baje. Kya main ise book kar doon?"
                : provider + " के साथ " + date + " को " + time + " बजे। क्या मैं इसे बुक कर दूँ?";
        return provider + " on " + date + " at " + time + ". Shall I book it?";
    }

    private String noAppointments(AppointmentListPayload payload, boolean hinglish, boolean hindi) {
        AppliedFilters filters = payload.appliedFilters();
        if (filters != null && filters.doctor() != null) {
            return hindi ? (hinglish ? "Aapki " + filters.doctor() + " ke saath koi upcoming appointment nahi hai."
                    : filters.doctor() + " के साथ आपकी कोई आगामी अपॉइंटमेंट नहीं है।")
                    : "You do not have any upcoming appointments with " + filters.doctor() + ".";
        }
        if (filters != null && filters.date() != null) {
            String date = formatDate(filters.date(), hinglish, hindi);
            return hindi ? (hinglish ? date + " ko aapki koi upcoming appointment nahi hai."
                    : date + " को आपकी कोई आगामी अपॉइंटमेंट नहीं है।")
                    : "You do not have any upcoming appointments on " + date + ".";
        }
        return hindi ? (hinglish ? "Aapki koi upcoming appointment nahi mili." : "आपकी कोई आगामी अपॉइंटमेंट नहीं मिली।")
                : "You do not have any upcoming appointments.";
    }

    private String appointments(AppointmentListPayload payload, boolean hinglish, boolean hindi) {
        List<AppointmentFact> values = payload.appointments();
        if (values.isEmpty()) return hindi ? (hinglish ? "Aapki appointment details abhi nahi mil paayi."
                : "आपकी अपॉइंटमेंट का विवरण अभी उपलब्ध नहीं है।") : "Appointment details are unavailable.";
        if (values.size() == 1) return appointmentLine(values.getFirst(), hinglish, hindi);
        String lines = numberedAppointments(values, hinglish, hindi);
        if (hindi) return hinglish ? "Aapki " + values.size() + " upcoming appointments hain: " + lines
                : "आपकी " + values.size() + " आगामी अपॉइंटमेंट हैं: " + lines;
        return "You have " + values.size() + " upcoming appointments: " + lines;
    }

    private String appointmentChoices(List<AppointmentFact> candidates, String action, boolean hinglish, boolean hindi) {
        if (candidates.isEmpty()) return action.equals("cancel") ? (hindi
                ? (hinglish ? "Cancel karne ke liye koi appointment nahi mili." : "रद्द करने के लिए कोई अपॉइंटमेंट नहीं मिली।")
                : "I couldn't find an appointment to cancel.") : (hindi
                ? (hinglish ? "Reschedule karne ke liye koi appointment nahi mili." : "रीशेड्यूल करने के लिए कोई अपॉइंटमेंट नहीं मिली।")
                : "I couldn't find an appointment to reschedule.");
        String lines = numberedAppointments(candidates, hinglish, hindi);
        if (hindi) return hinglish ? "Mujhe ye appointments mili: " + lines + ". Aap kise " + action + " karna chahenge?"
                : "मुझे ये अपॉइंटमेंट मिलीं: " + lines + "। आप किसे " + (action.equals("cancel") ? "रद्द" : "रीशेड्यूल") + " करना चाहेंगे?";
        return "I found more than one matching appointment: " + lines + ". Which one would you like to " + action + "?";
    }

    private String cancellationConfirmation(AppointmentFact appointment, boolean hinglish, boolean hindi) {
        if (appointment == null) return hindi ? (hinglish ? "Kaunsi appointment cancel karni hai?" : "कौन-सी अपॉइंटमेंट रद्द करनी है?")
                : "Which appointment would you like to cancel?";
        String line = appointmentCore(appointment, hinglish, hindi);
        if (hindi) return hinglish ? "Aapki " + line + " appointment hai. Kya main ise cancel kar doon?"
                : "आपकी " + line + " अपॉइंटमेंट है। क्या मैं इसे रद्द कर दूँ?";
        return "You have " + line + ". Shall I cancel it?";
    }

    private String cancellationSuccess(AppointmentFact appointment, boolean hinglish, boolean hindi) {
        if (appointment == null) return hindi ? (hinglish ? "Appointment cancel ho gayi hai." : "अपॉइंटमेंट रद्द कर दी गई है।")
                : "Your appointment has been cancelled.";
        String line = appointmentCore(appointment, hinglish, hindi);
        if (hindi) return hinglish ? "Aapki " + line + " appointment cancel ho gayi hai."
                : "आपकी " + line + " अपॉइंटमेंट रद्द कर दी गई है।";
        return "Your appointment " + line + " has been cancelled.";
    }

    private String rescheduleNeedDate(RescheduleNeedDatePayload payload, boolean hinglish, boolean hindi) {
        AppointmentFact original = payload.originalAppointment();
        if (original == null) return hindi ? (hinglish ? "Aap appointment kis date par shift karna chahenge?"
                : "आप अपॉइंटमेंट किस तारीख पर बदलना चाहेंगे?") : "What date would you like to move it to?";
        String line = appointmentCore(original, hinglish, hindi);
        if (hindi) return hinglish ? "Aapki " + line + " appointment hai. Ise kis date par move karna chahenge?"
                : "आपकी " + line + " अपॉइंटमेंट है। आप इसे किस तारीख पर शिफ्ट करना चाहेंगे?";
        return "You have " + line + ". What date would you like to move it to?";
    }

    private String rescheduleConfirmation(RescheduleConfirmationPayload payload, boolean hinglish, boolean hindi) {
        AppointmentFact original = payload.originalAppointment();
        String provider = safe(payload.providerDisplayName(), hindi ? "डॉक्टर" : "Doctor");
        String connector = hindi ? (hinglish ? " ko " : " को ") : " at ";
        String from = original == null ? "" : formatDate(original.date(), hinglish, hindi) + connector + formatTime(original.time());
        String to = formatDate(payload.targetDate(), hinglish, hindi) + connector + formatTime(payload.targetTime());
        if (hindi) return hinglish ? provider + " ke saath appointment " + from + " se " + to + " par shift kar doon?"
                : provider + " के साथ " + from + " की अपॉइंटमेंट को " + to + " पर शिफ्ट कर दूँ?";
        return "Move your appointment with " + provider + " from " + from + " to " + to + "? Shall I reschedule it?";
    }

    private String rescheduleSuccess(RescheduleSuccessPayload payload, boolean hinglish, boolean hindi) {
        String date = formatDate(payload.newDate(), hinglish, hindi);
        String time = formatTime(payload.newTime());
        if (hindi) return hinglish ? "Aapki appointment " + date + " ko " + time + " baje ke liye reschedule ho gayi hai."
                : "आपकी अपॉइंटमेंट " + date + " को " + time + " बजे के लिए रीशेड्यूल हो गई है।";
        return "Your appointment has been rescheduled to " + date + " at " + time + ".";
    }

    private String clarification(ClarificationPayload payload, boolean hinglish, boolean hindi) {
        String code = payload.reasonCode();
        if ("PAST_DATE".equals(code)) return hindi ? (hinglish ? "Yeh date beet chuki hai. Aaj ya aage ki date chunen."
                : "यह तारीख बीत चुकी है। कृपया आज या भविष्य की तारीख चुनें।") : "That date is in the past. Please choose today or a future date.";
        if ("INVALID".equals(code)) return hindi ? (hinglish ? "Main is time ko samajh nahi paaya."
                : "मैं इस समय को समझ नहीं पाया।") : "I couldn't use that time.";
        if ("LOOKUP_FILTER_AMBIGUOUS".equals(code)) return hindi ? (hinglish ? "Kis doctor ya specialty ki appointment?"
                : "क्या आप किसी खास डॉक्टर या विशेषज्ञ की अपॉइंटमेंट पूछ रहे हैं?") : "Are you asking about an appointment with a specific doctor or specialty?";
        if (hindi) return hinglish ? "Appointment ke baare mein thoda aur bata sakte hain?"
                : "अपॉइंटमेंट के बारे में थोड़ा और बता सकते हैं?";
        return "How can I help with an appointment?";
    }

    private String callToBook(CallToBookPayload payload, boolean hinglish, boolean hindi) {
        String name = safe(payload.providerDisplayName(), hindi ? "डॉक्टर" : "the doctor");
        if (hindi) return hinglish ? name + " ke liye online booking available nahi hai. Clinic se sampark karein."
                : name + " के लिए ऑनलाइन बुकिंग उपलब्ध नहीं है। कृपया क्लिनिक से संपर्क करें।";
        return "Online scheduling is not connected for " + name + ". Please contact the clinic directly.";
    }

    private String appointmentLine(AppointmentFact appointment, boolean hinglish, boolean hindi) {
        String core = appointmentCore(appointment, hinglish, hindi);
        if (hindi) return hinglish ? "Aapki " + core + " appointment hai."
                : "आपकी " + core + " अपॉइंटमेंट है।";
        return "You have an appointment " + core + ".";
    }

    private String appointmentCore(AppointmentFact appointment, boolean hinglish, boolean hindi) {
        String doctor = safe(appointment.providerDisplayName(), hindi ? "आपके डॉक्टर" : "your doctor");
        String date = appointment.date() == null ? (hindi ? "निर्धारित तारीख" : "the scheduled date")
                : formatDate(appointment.date(), hinglish, hindi);
        String time = appointment.time() == null ? (hindi ? "निर्धारित समय" : "the scheduled time")
                : formatTime(appointment.time());
        if (hindi) return hinglish ? doctor + " ke saath " + date + " ko " + time + " baje"
                : doctor + " के साथ " + date + " को " + time + " बजे";
        return "with " + doctor + " on " + date + " at " + time;
    }

    private String numberedAppointments(List<AppointmentFact> values, boolean hinglish, boolean hindi) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) lines.add((i + 1) + ". " + appointmentCore(values.get(i), hinglish, hindi));
        return String.join("; ", lines);
    }

    private String choices(List<String> names) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < names.size(); i++) labels.add((i + 1) + ". " + safe(names.get(i), "Doctor"));
        return String.join("; ", labels);
    }

    private String slotLabels(List<SlotFact> slots) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < slots.size(); i++) labels.add((i + 1) + ". " + safe(slots.get(i).displayTime(), formatTime(slots.get(i).startsAt())));
        return String.join("; ", labels);
    }

    private String formatDate(LocalDate date, boolean hinglish, boolean hindi) {
        if (date == null) return "";
        return date.format(hindi && !hinglish ? HI_DATE : EN_DATE);
    }

    private String formatTime(LocalTime time) { return time == null ? "" : time.toString(); }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
