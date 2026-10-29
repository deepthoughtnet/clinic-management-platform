package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Pure renderer for authorized Voice response intents; it does not interpret or route user turns. */
public final class AivaV2VoiceResponseRenderer {
    private final AivaV2VoiceTimePresentationFormatter timeFormatter = new AivaV2VoiceTimePresentationFormatter();

    public String render(Intent intent) {
        if (intent == null) return "";
        boolean hindi = intent.presentationMode() != VoicePresentationMode.ENGLISH;
        boolean hinglish = intent.presentationMode() == VoicePresentationMode.HINGLISH;
        String provider = speechName(cleanName(intent.providerName()), intent.presentationMode());
        String target = speechName(cleanName(intent.targetProviderName()), intent.presentationMode());
        String date = spokenDate(intent.date(), intent.presentationMode());
        String time = spokenTime(intent.time(), hindi, hinglish);
        return switch (intent.type()) {
            case PROVIDER_CLARIFICATION -> providerClarification(intent.candidates(), hindi, hinglish,
                    intent.fallbackText());
            case DATE_CLARIFICATION -> !provider.isEmpty()
                    ? hindi ? hinglish ? "Dr. " + provider + " ke liye aaj, kal ya parson mein se kis date ka appointment chahiye?"
                            : "डॉ. " + provider + " के लिए आज, कल या परसों में से किस तारीख का appointment चाहिए?"
                    : "Would you like an appointment with Dr. " + provider + " today, tomorrow, or another date?"
                    : fallback(intent.fallbackText(), hindi
                            ? hinglish ? "Aaj, kal ya parson — kis date ke slots dekhna chahenge?"
                                    : "आज, कल या परसों — किस तारीख के slots देखना चाहेंगे?"
                            : "Which date would you like to check?", hindi, hinglish);
            case DAYPART_CLARIFICATION -> daypartClarification(date, intent.dayparts(), hindi, hinglish,
                    intent.fallbackText());
            case SLOT_CHOICES -> slotChoices(date, intent.slots(), hindi, hinglish, intent.fallbackText());
            case SLOT_EXHAUSTED -> hindi ? hinglish
                    ? "Is date ke liye aur slots available nahi hain. Kya aap doosri date dekhna chahenge?"
                    : "इस तारीख के लिए और स्लॉट उपलब्ध नहीं हैं। क्या आप दूसरी तारीख देखना चाहेंगे?"
                    : "There are no more slots for this date. Would you like to see another date?";
            case PROVIDER_SWITCH_CONFIRMATION -> providerSwitch(provider, target, hindi, hinglish,
                    intent.fallbackText());
            case BOOKING_CONFIRMATION -> bookingConfirmation(provider, date, time, hindi, hinglish,
                    intent.fallbackText());
            case BOOKING_SUCCESS -> bookingOutcome(provider, date, time, intent.bookingReference(), hindi,
                    hinglish, false, intent.fallbackText());
            case COMPLETED_BOOKING_REPEAT -> bookingOutcome(provider, date, time, intent.bookingReference(), hindi,
                    hinglish, true, intent.fallbackText());
            case BOOKING_STALE -> hindi ? hinglish
                    ? "Yeh slot ab available nahi hai. Aap diye gaye slots mein se doosra time chun sakte hain."
                    : "यह स्लॉट अब उपलब्ध नहीं है। कृपया दिए गए slots में से दूसरा समय चुनें।"
                    : "That slot is no longer available. Please choose another displayed time.";
            case BOOKING_FAILED -> hindi ? hinglish
                    ? "Appointment complete nahi ho paya. Status verify kiye bina main ise dobara submit nahi karunga."
                : "Appointment पूरा नहीं हो पाया। स्थिति जाँचे बिना इसे दोबारा submit नहीं किया जाएगा।"
                    : "The appointment could not be completed. I will not retry until its status is verified.";
            case BOOKING_STATUS_UNKNOWN -> hindi ? hinglish
                    ? "Booking ka final status abhi confirm nahi ho pa raha. Status clear hone tak main ise dobara create nahi karunga."
                    : "Booking की अंतिम स्थिति अभी confirm नहीं हो पा रही। स्थिति स्पष्ट होने तक इसे दोबारा नहीं बनाया जाएगा।"
                    : "I cannot confirm the booking status yet. I will not create it again until the status is clear.";
            case BOOKING_DECLINED -> hindi ? hinglish
                    ? "Theek hai. Appointment book nahi hui. Aap koi doosra slot chun sakte hain."
                    : "ठीक है। कोई appointment book नहीं हुई। आप दूसरा slot चुन सकते हैं।"
                    : "Understood. No appointment was booked. You can choose another displayed slot.";
            case CONTEXTUAL_CLARIFICATION -> fallback(intent.fallbackText(), hindi
                    ? hinglish ? "Dikhaaye gaye options mein se ek chuniye." : "दिए गए विकल्पों में से एक चुनिए।"
                    : "Please choose one of the available options.", hindi, hinglish);
            case COMPATIBILITY -> fallback(intent.fallbackText(), "", hindi, hinglish);
        };
    }

    private String providerClarification(List<String> candidates, boolean hindi, boolean hinglish, String fallback) {
        List<String> safe = candidates == null ? List.of() : candidates.stream().filter(StringUtils::hasText)
                .map(this::cleanName).limit(3).toList();
        if (safe.size() == 1) return hindi ? hinglish ? "Kya aap Dr. " + safe.getFirst() + " ki baat kar rahe hain?"
                : "क्या आप डॉक्टर " + safe.getFirst() + " की बात कर रहे हैं?"
                : "Do you mean Dr. " + safe.getFirst() + "?";
        if (safe.size() >= 2) {
            String joined = String.join(hindi ? hinglish ? " ya " : " या " : " or ", safe);
            return hindi ? hinglish ? joined + " — kis doctor ke saath appointment chahenge?"
                    : "कृपया बताइए, " + joined + " में से किस डॉक्टर के साथ appointment चाहिए?"
                    : "Which doctor would you like — " + joined + "?";
        }
        return hindi ? hinglish ? "Doctor ka naam ek baar phir bataiye." : "कृपया डॉक्टर का नाम एक बार फिर बताइए।"
                : "Please repeat the doctor's name.";
    }

    private String daypartClarification(String date, List<String> dayparts, boolean hindi, boolean hinglish,
                                        String fallback) {
        List<String> safe = dayparts == null ? List.of() : dayparts.stream().filter(StringUtils::hasText).limit(3).toList();
        if (safe.isEmpty()) return fallback(fallback, hindi ? "सुबह या शाम में से कौन-सा समय देखना चाहेंगे?"
                : "Would you prefer morning or evening?", hindi, hinglish);
        String dateText = StringUtils.hasText(date) ? " " + date : "";
        String options = String.join(hindi ? hinglish ? " ya " : " या " : " or ", safe);
        return hindi ? hinglish ? dateText.trim() + (dateText.isEmpty() ? "" : " ke liye")
                + " " + options + " mein se kaunsa time dekhein?"
                : (dateText.isEmpty() ? "" : dateText + " के लिए ") + options + " में से कौन-सा समय देखना चाहेंगे?"
                : (dateText.isEmpty() ? "" : "For " + date + ", ") + "would you prefer " + options + "?";
    }

    private String slotChoices(String date, List<LocalTime> slots, boolean hindi, boolean hinglish, String fallback) {
        List<LocalTime> safe = slots == null ? List.of() : slots.stream().filter(java.util.Objects::nonNull).limit(3).toList();
        if (safe.isEmpty()) return fallback(fallback, hindi ? "दिए गए समय में से कोई स्लॉट चुनें।"
                : "Please choose a displayed slot.", hindi, hinglish);
        String options = java.util.stream.IntStream.range(0, safe.size())
                .mapToObj(index -> (index + 1) + ". " + spokenTime(safe.get(index), hindi, hinglish))
                .reduce((left, right) -> left + ", " + right).orElse("");
        String dateText = StringUtils.hasText(date) ? date + " " : "";
        return hindi ? hinglish ? dateText + "available slots hain: " + options + ". Inmein se koi choose karein ya 'aur slots' bolein."
                : dateText + "उपलब्ध समय हैं: " + options + ". इनमें से कोई चुनें या 'और समय' कहें।"
                : "Available slots" + (dateText.isEmpty() ? "" : " for " + date.trim()) + " are: " + options
                        + ". Choose one, or say ‘more slots’.";
    }

    private String providerSwitch(String current, String target, boolean hindi, boolean hinglish, String fallback) {
        if (current.isEmpty() || target.isEmpty()) return fallback(fallback,
                hindi ? "क्या आप डॉक्टर बदलना चाहते हैं?" : "Would you like to change doctors?", hindi, hinglish);
        return hindi ? hinglish ? "Abhi aapka appointment flow Dr. " + current + " ke saath hai. Dr. " + target
                + " par switch karne se current slot selection reset ho jayegi. Kya aap switch karna chahenge?"
                : "अभी आपका appointment flow डॉक्टर " + current + " के साथ है। डॉक्टर " + target
                + " पर switch करने से मौजूदा slot selection reset हो जाएगी। क्या आप switch करना चाहते हैं?"
                : "Your appointment flow is with Dr. " + current + ". Switching to Dr. " + target
                + " will reset the current slot selection. Would you like to switch?";
    }

    private String bookingConfirmation(String provider, String date, String time, boolean hindi, boolean hinglish,
                                       String fallback) {
        if (provider.isEmpty() || date.isEmpty() || time.isEmpty()) return fallback(fallback,
                hindi ? "क्या मैं यह appointment book कर दूँ?" : "Would you like me to book this appointment?", hindi, hinglish);
        return hindi ? hinglish ? "Dr. " + provider + ", " + date + " ko " + time + ". Kya main ye appointment book kar doon?"
                : "डॉक्टर " + provider + ", " + date + " को " + time + "। क्या मैं यह appointment book कर दूँ?"
                : "Dr. " + provider + ", " + date + " at " + time + ". Would you like me to book this appointment?";
    }

    private String bookingOutcome(String provider, String date, String time, String reference, boolean hindi,
                                  boolean hinglish, boolean repeat, String fallback) {
        if (provider.isEmpty() || date.isEmpty() || time.isEmpty()) return fallback(fallback,
                hindi ? "आपका appointment book हो गया है।" : "Your appointment is booked.", hindi, hinglish);
        String text = hindi ? hinglish ? "Aapka appointment Dr. " + provider + " ke saath " + date + " ko " + time
                + " book ho gaya hai." : "आपका appointment डॉक्टर " + provider + " के साथ " + date + " को " + time
                + " बुक हो गया है।"
                : "Your appointment with Dr. " + provider + " on " + date + " at " + time + " is booked.";
        return StringUtils.hasText(reference) ? text + " Reference: " + reference + "." : text;
    }

    private String spokenDate(LocalDate date, VoicePresentationMode mode) {
        if (date == null) return "";
        boolean hindi = mode != VoicePresentationMode.ENGLISH;
        ResponseStyle style = mode == VoicePresentationMode.HINGLISH ? ResponseStyle.HINGLISH : ResponseStyle.STANDARD;
        return AivaSpokenDateFormatter.format(date, hindi ? "hi" : "en", style);
    }

    private String spokenTime(LocalTime time, boolean hindi, boolean hinglish) {
        VoicePresentationMode mode = !hindi ? VoicePresentationMode.ENGLISH
                : hinglish ? VoicePresentationMode.HINGLISH : VoicePresentationMode.HINDI;
        return timeFormatter.format(time, mode);
    }

    private String speechName(String name, VoicePresentationMode mode) {
        if (!StringUtils.hasText(name) || mode != VoicePresentationMode.HINDI) return name;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "amit verma" -> "अमित वर्मा";
            case "ravi mehta" -> "रवि मेहता";
            case "ravi sharma" -> "रवि शर्मा";
            case "pooja rao" -> "पूजा राव";
            default -> name;
        };
    }

    private String cleanName(String name) {
        return StringUtils.hasText(name)
                ? name.trim().replaceFirst("(?iu)^(?:(?:dr\\.?|doctor|doc)\\s+)+", "") : "";
    }

    private String fallback(String value, String otherwise, boolean hindi, boolean hinglish) {
        if (!StringUtils.hasText(value)) return otherwise;
        if (!hindi || value.matches("(?s).*[\\u0900-\\u097F].*")) return value;
        if (hinglish && value.matches("(?iu).*(?:\\b(?:aap|hai|hain|ka|ke|ki|karo|batao|dikhao|chahiye|nahi|nahi|ho)\\b).*")) {
            return value;
        }
        return otherwise;
    }

    public enum Type {
        PROVIDER_CLARIFICATION, DATE_CLARIFICATION, DAYPART_CLARIFICATION, SLOT_CHOICES, SLOT_EXHAUSTED,
        PROVIDER_SWITCH_CONFIRMATION, BOOKING_CONFIRMATION, BOOKING_SUCCESS, BOOKING_STALE, BOOKING_FAILED,
        BOOKING_STATUS_UNKNOWN, BOOKING_DECLINED, COMPLETED_BOOKING_REPEAT, CONTEXTUAL_CLARIFICATION, COMPATIBILITY
    }

    public record Intent(Type type, VoicePresentationMode presentationMode, String providerName,
                         String targetProviderName, List<String> candidates, LocalDate date, LocalTime time,
                         List<LocalTime> slots, List<String> dayparts, String bookingReference, String fallbackText) {
        public Intent {
            type = type == null ? Type.COMPATIBILITY : type;
            presentationMode = presentationMode == null ? VoicePresentationMode.HINDI : presentationMode;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            slots = slots == null ? List.of() : List.copyOf(slots);
            dayparts = dayparts == null ? List.of() : List.copyOf(dayparts);
        }
    }
}
