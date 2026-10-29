package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/** Formats canonical dates for speech-facing AIVA responses, independently of UI/diagnostic formats. */
public final class AivaSpokenDateFormatter {
    private static final String[] HINDI_MONTHS = {
            "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
            "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
    };

    private AivaSpokenDateFormatter() { }

    public static String format(LocalDate date, String responseLanguage, ResponseStyle responseStyle) {
        if (date == null) return "";
        boolean hindi = responseLanguage != null
                && responseLanguage.toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && responseStyle == ResponseStyle.HINGLISH;
        String month = hindi && !hinglish
                ? HINDI_MONTHS[date.getMonthValue() - 1]
                : englishMonth(date.getMonth());
        return date.getDayOfMonth() + " " + month + " " + date.getYear();
    }

    /** Convenience for existing renderers which have already resolved locale/style flags. */
    public static String format(LocalDate date, boolean hinglish, boolean hindi) {
        return format(date, hindi ? "hi" : "en",
                hinglish ? ResponseStyle.HINGLISH : ResponseStyle.STANDARD);
    }

    private static String englishMonth(Month month) {
        return month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
    }
}
