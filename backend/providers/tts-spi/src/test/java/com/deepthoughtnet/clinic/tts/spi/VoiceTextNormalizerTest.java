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

    @Test
    void normalizesOnlyHindiTemporalValuesWithoutRewritingVisibleWording() {
        String text = "आपकी अपॉइंटमेंट 23 सितंबर 2026 को 20:00 बजे है।";

        assertThat(normalizer.normalizeTemporalValuesForVoice(text, "hi-IN"))
                .isEqualTo("आपकी अपॉइंटमेंट तेईस सितंबर दो हज़ार छब्बीस को शाम आठ बजे है।");
        assertThat(text).isEqualTo("आपकी अपॉइंटमेंट 23 सितंबर 2026 को 20:00 बजे है।");
    }

    @Test
    void leavesEnglishTemporalValuesUnchanged() {
        String text = "Your appointment is on 23 September 2026 at 20:00.";

        assertThat(normalizer.normalizeTemporalValuesForVoice(text, "en-IN")).isEqualTo(text);
    }
}
