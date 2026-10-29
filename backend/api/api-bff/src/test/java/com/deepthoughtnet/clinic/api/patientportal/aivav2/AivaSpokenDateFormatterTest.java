package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AivaSpokenDateFormatterTest {
    @Test
    void formatsHindiDatesWithFullHindiMonthNames() {
        assertThat(AivaSpokenDateFormatter.format(LocalDate.of(2026, 10, 22), "hi-IN", ResponseStyle.STANDARD))
                .isEqualTo("22 अक्टूबर 2026");
        assertThat(AivaSpokenDateFormatter.format(LocalDate.of(2026, 10, 26), "hi-IN", ResponseStyle.STANDARD))
                .isEqualTo("26 अक्टूबर 2026");
        assertThat(AivaSpokenDateFormatter.format(LocalDate.of(2026, 11, 5), "hi-IN", ResponseStyle.STANDARD))
                .isEqualTo("5 नवंबर 2026");
    }

    @Test
    void formatsHinglishAndEnglishDatesWithFullEnglishMonthNames() {
        LocalDate date = LocalDate.of(2026, 10, 22);
        assertThat(AivaSpokenDateFormatter.format(date, "hi-IN", ResponseStyle.HINGLISH))
                .isEqualTo("22 October 2026");
        assertThat(AivaSpokenDateFormatter.format(date, "en-IN", ResponseStyle.STANDARD))
                .isEqualTo("22 October 2026");
    }
}
