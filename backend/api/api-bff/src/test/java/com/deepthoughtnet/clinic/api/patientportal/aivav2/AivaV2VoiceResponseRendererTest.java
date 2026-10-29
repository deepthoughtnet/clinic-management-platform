package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AivaV2VoiceResponseRendererTest {
    private final AivaV2VoiceResponseRenderer renderer = new AivaV2VoiceResponseRenderer();

    @Test
    void rendersProviderDateAndDaypartClarificationsFromSuppliedContext() {
        var provider = intent(AivaV2VoiceResponseRenderer.Type.PROVIDER_CLARIFICATION,
                VoicePresentationMode.HINGLISH, null, null, List.of("Ravi Mehta", "Ravi Sharma"), null, null,
                List.of(), List.of(), null, null);
        var date = intent(AivaV2VoiceResponseRenderer.Type.DATE_CLARIFICATION,
                VoicePresentationMode.HINGLISH, "Amit Verma", null, List.of(), null, null,
                List.of(), List.of(), null, null);
        var daypart = intent(AivaV2VoiceResponseRenderer.Type.DAYPART_CLARIFICATION,
                VoicePresentationMode.HINDI, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 27), null,
                List.of(), List.of("सुबह", "शाम"), null, null);

        assertThat(renderer.render(provider)).contains("Ravi Mehta", "Ravi Sharma", "kis doctor");
        var noCanonicalCandidates = intent(AivaV2VoiceResponseRenderer.Type.PROVIDER_CLARIFICATION,
                VoicePresentationMode.ENGLISH, null, null, List.of(), null, null, List.of(), List.of(), null,
                "Do you mean Dr. Fabricated Provider?");
        assertThat(renderer.render(noCanonicalCandidates)).contains("repeat the doctor's name")
                .doesNotContain("Fabricated Provider");
        assertThat(renderer.render(date)).contains("Dr. Amit Verma", "kis date");
        assertThat(renderer.render(daypart)).contains("27 अक्टूबर", "सुबह", "शाम");
    }

    @Test
    void rendersOnlySuppliedSlotPageAndExhaustionAction() {
        var slots = intent(AivaV2VoiceResponseRenderer.Type.SLOT_CHOICES,
                VoicePresentationMode.HINGLISH, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 27), null,
                List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)), List.of(), null, null);
        var exhausted = intent(AivaV2VoiceResponseRenderer.Type.SLOT_EXHAUSTED,
                VoicePresentationMode.HINDI, null, null, List.of(), null, null, List.of(), List.of(), null, null);

        String rendered = renderer.render(slots);
        assertThat(rendered).contains("evening 5 baje", "evening 5:30", "aur slots").doesNotContain("6:00");
        assertThat(renderer.render(exhausted)).contains("और स्लॉट उपलब्ध नहीं", "दूसरी तारीख");
    }

    @Test
    void hindiSlotAndConfirmationTimesUseNaturalSpokenHindi() {
        var slots = intent(AivaV2VoiceResponseRenderer.Type.SLOT_CHOICES,
                VoicePresentationMode.HINDI, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 29), null,
                List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0)), List.of(), null, null);
        var confirmation = intent(AivaV2VoiceResponseRenderer.Type.BOOKING_CONFIRMATION,
                VoicePresentationMode.HINDI, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 29),
                LocalTime.of(19, 30), List.of(), List.of(), null, null);

        assertThat(renderer.render(slots)).contains("शाम पाँच बजे", "शाम साढ़े पाँच बजे", "शाम छह बजे")
                .contains("उपलब्ध समय हैं").doesNotContain(" PM", " AM", "slots हैं");
        assertThat(renderer.render(confirmation)).contains("अमित वर्मा", "शाम साढ़े सात बजे")
                .doesNotContain("PM", "19:30");
    }

    @Test
    void rendersSwitchAndConfirmationWithCanonicalProviderDateAndTime() {
        var change = intent(AivaV2VoiceResponseRenderer.Type.PROVIDER_SWITCH_CONFIRMATION,
                VoicePresentationMode.HINGLISH, "Amit Verma", "Ravi Mehta", List.of(), null, null,
                List.of(), List.of(), null, null);
        var confirm = intent(AivaV2VoiceResponseRenderer.Type.BOOKING_CONFIRMATION,
                VoicePresentationMode.HINGLISH, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 27),
                LocalTime.of(17, 30), List.of(), List.of(), null, null);

        assertThat(renderer.render(change)).contains("Amit Verma", "Ravi Mehta", "reset ho jayegi", "switch");
        assertThat(renderer.render(confirm)).contains("Amit Verma", "27 October", "evening 5:30", "book kar doon");
    }

    @Test
    void distinguishesSuccessRepeatStaleGenericFailureAndUnknownStatus() {
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.BOOKING_SUCCESS))
                .contains("book ho gaya").doesNotContain("request received");
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.COMPLETED_BOOKING_REPEAT))
                .contains("book ho gaya", "Reference: APT-1");
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.BOOKING_STALE))
                .contains("slot ab available nahi hai").doesNotContain("STALE_SLOT");
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.BOOKING_FAILED))
                .contains("complete nahi ho paya").doesNotContain("STALE_SLOT");
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.BOOKING_STATUS_UNKNOWN))
                .contains("final status abhi confirm nahi ho pa raha").doesNotContain("book ho gaya");
        assertThat(renderOutcome(AivaV2VoiceResponseRenderer.Type.BOOKING_DECLINED))
                .contains("Appointment book nahi hui");
        assertThat(renderer.render(intent(AivaV2VoiceResponseRenderer.Type.CONTEXTUAL_CLARIFICATION,
                VoicePresentationMode.HINDI, null, null, List.of(), null, null, List.of(), List.of(), null,
                "क्या आप दिए गए समय में से चुनना चाहते हैं?")))
                .contains("दिए गए समय में से चुनना चाहते हैं");
    }

    @Test
    void localizedRendererDoesNotExposeInternalIdentifiersOrOperationNames() {
        String rendered = renderer.render(intent(AivaV2VoiceResponseRenderer.Type.BOOKING_CONFIRMATION,
                VoicePresentationMode.ENGLISH, "Amit Verma", null, List.of(), LocalDate.of(2026, 10, 27),
                LocalTime.of(17, 30), List.of(), List.of(), null, null));
        assertThat(rendered).contains("Dr. Amit Verma", "27 October", "5:30 PM")
                .doesNotContain("CARE_PRIVATE", "STALE_SLOT", "draftRevision", "BOOKING_CONFIRMATION", "550e8400-");
    }

    private String renderOutcome(AivaV2VoiceResponseRenderer.Type type) {
        return renderer.render(intent(type, VoicePresentationMode.HINGLISH, "Amit Verma", null, List.of(),
                LocalDate.of(2026, 10, 27), LocalTime.of(17, 30), List.of(), List.of(), "APT-1", null));
    }

    private AivaV2VoiceResponseRenderer.Intent intent(AivaV2VoiceResponseRenderer.Type type,
            VoicePresentationMode mode, String provider, String target, List<String> candidates, LocalDate date,
            LocalTime time, List<LocalTime> slots, List<String> dayparts, String reference, String fallback) {
        return new AivaV2VoiceResponseRenderer.Intent(type, mode, provider, target, candidates, date, time,
                slots, dayparts, reference, fallback);
    }
}
