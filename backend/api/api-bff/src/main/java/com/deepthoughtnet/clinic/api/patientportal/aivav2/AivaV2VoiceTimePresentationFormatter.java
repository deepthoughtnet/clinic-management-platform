package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deterministic spoken-time formatting; canonical LocalTime values are never changed. */
public final class AivaV2VoiceTimePresentationFormatter {
    private static final DateTimeFormatter ENGLISH = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final String[] HINDI = {"बारह", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस", "ग्यारह"};

    public String format(LocalTime time, VoicePresentationMode mode) {
        if (time == null) return "";
        if (mode == VoicePresentationMode.ENGLISH) return ENGLISH.format(time);
        int hour24 = time.getHour();
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        if (mode == VoicePresentationMode.HINGLISH) {
            String period = hour24 < 12 ? "morning" : hour24 < 17 ? "afternoon" : "evening";
            String spoken = time.getMinute() == 0 ? hour12 + " baje" : hour12 + ":" + String.format(Locale.ROOT, "%02d", time.getMinute());
            return period + " " + spoken;
        }
        String period = hour24 < 12 ? "सुबह" : hour24 < 17 ? "दोपहर" : "शाम";
        String spokenHour = HINDI[hour12 % 12];
        if (time.getMinute() == 30) return period + " साढ़े " + spokenHour + " बजे";
        if (time.getMinute() == 0) return period + " " + spokenHour + " बजे";
        return period + " " + spokenHour + " बजकर " + hindiNumber(time.getMinute()) + " मिनट";
    }

    private String hindiNumber(int number) {
        if (number >= 0 && number < 12) return HINDI[number];
        return Integer.toString(number);
    }
}
