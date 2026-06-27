package com.deepthoughtnet.clinic.tts.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VoiceTextNormalizerTest {
    private final VoiceTextNormalizer normalizer = new VoiceTextNormalizer();

    @Test
    void normalizesHindiVoiceAppointmentPrompt() {
        String normalized = normalizer.normalizeForVoice(
                "Dr Ashish Shri के लिए 2026-06-29 को 09:30 बुक कर दूँ?",
                "hi-IN"
        );

        assertThat(normalized).isEqualTo(
                "डॉक्टर आशीष श्री के लिए उनतीस जून दो हज़ार छब्बीस, सुबह साढ़े नौ बजे बुक कर दूँ?"
        );
    }

    @Test
    void translatesCommonEnglishAppointmentHeadingToHindi() {
        String normalized = normalizer.normalizeForVoice("Here are your upcoming appointments:", "hi-IN");

        assertThat(normalized).isEqualTo("आपकी आने वाली अपॉइंटमेंट ये हैं:");
    }

    @Test
    void normalizesAppointmentListSeparatorsDatesAndTimes() {
        String normalized = normalizer.normalizeForVoice("Vikas Singh · 27 Jun 2026 · 10:00", "hi-IN");

        assertThat(normalized).isEqualTo("डॉक्टर विकास सिंह, सत्ताईस जून दो हज़ार छब्बीस, सुबह दस बजे");
    }

    @Test
    void leavesNonHindiTextUnchanged() {
        String text = "Here are your upcoming appointments:";

        assertThat(normalizer.normalizeForVoice(text, "en-IN")).isEqualTo(text);
    }
}
