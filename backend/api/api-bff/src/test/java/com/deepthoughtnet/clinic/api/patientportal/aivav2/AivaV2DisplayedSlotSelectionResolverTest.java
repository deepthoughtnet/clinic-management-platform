package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2DisplayedSlotSelectionResolverTest {
    @Test
    void hindiOrdinalSlotPhraseResolvesAgainstCurrentPage() {
        var turn = LanguageAdapterRegistry.defaults().resolve("hi-IN", "पहला स्लॉट")
                .normalize("पहला स्लॉट", LocalDate.of(2026, 10, 26));
        var availability = availability();

        var selection = new AivaV2DisplayedSlotSelectionResolver().resolve(turn, availability);

        assertThat(selection.recognized()).isTrue();
        assertThat(selection.selection().ordinal()).isEqualTo(1);
    }

    @Test
    void hindiAndHinglishSpokenTimesResolveOnlyToCurrentDisplayedCanonicalPage() {
        var resolver = new AivaV2DisplayedSlotSelectionResolver();
        var page = availabilityAtSecondPage();
        for (String[] sample : new String[][]{
                {"hi-IN", "सात तीस PM बुक कर दो"},
                {"hi-IN", "साढ़े सात वाला बुक करो"},
                {"hi-IN", "सात बजकर तीस वाला"},
                {"hi-IN", "सात तीस पी एम"},
                {"hi-IN", "शाम साढ़े सात"},
                {"hi-IN", "7:30 wala book karo"},
                {"hi-IN", "seven thirty PM"},
                {"hi-IN", "seven thirty wala book karo"},
                {"hi-IN", "saat thirty wala book karo"},
                {"hi-IN", "saat baje thirty"},
                {"hi-IN", "saat tees"},
                {"hi-IN", "7.30"},
                {"hi-IN", "7 30"}}) {
            var turn = LanguageAdapterRegistry.defaults().resolve(sample[0], sample[1])
                    .normalize(sample[1], LocalDate.of(2026, 10, 26));
            var resolution = resolver.resolve(turn, page);
            assertThat(resolution.recognized()).as(sample[1]).isTrue();
            assertThat(resolution.selection().exactTime()).as(sample[1]).isEqualTo("19:30");
        }
    }

    @Test
    void ordinalEnglishPhraseIsNotMistakenForAClockTime() {
        var turn = LanguageAdapterRegistry.defaults().resolve("en-US", "second one")
                .normalize("second one", LocalDate.of(2026, 10, 26));
        var resolution = new AivaV2DisplayedSlotSelectionResolver().resolve(turn, availability());
        assertThat(resolution.selection().exactTime()).isNull();
        assertThat(resolution.selection().ordinal()).isEqualTo(2);
    }

    @Test
    void unmatchedSpokenTimeRemainsARecognizedCurrentPageClarification() {
        var turn = LanguageAdapterRegistry.defaults().resolve("hi-IN", "साढ़े आठ वाला")
                .normalize("साढ़े आठ वाला", LocalDate.of(2026, 10, 26));
        var resolution = new AivaV2DisplayedSlotSelectionResolver().resolve(turn, availabilityAtSecondPage());
        assertThat(resolution.recognized()).isTrue();
        assertThat(resolution.selection().exactTime()).isNull();
        assertThat(availabilityAtSecondPage().displayOffset()).isEqualTo(3);
    }

    @Test
    void hindiBookingPhrasesAndLastOrdinalResolveOnlyAgainstTheDisplayedLateEveningPage() {
        var resolver = new AivaV2DisplayedSlotSelectionResolver();
        var page = lateEveningPage();
        for (String phrase : List.of("नौ बजे", "नौ बजे वाला", "नौ बजे का", "नौ बजे का बुक कर दो",
                "नौ बजे का अपॉइंटमेंट बुक कर दो", "9 PM का बुक कर दो", "तीसरा वाला",
                "तीसरा स्लॉट", "आखिरी वाला")) {
            var turn = LanguageAdapterRegistry.defaults().resolve("hi-IN", phrase)
                    .normalize(phrase, LocalDate.of(2026, 10, 26));
            var resolution = resolver.resolve(turn, page);
            assertThat(resolution.recognized()).as(phrase).isTrue();
            if (phrase.contains("तीसरा")) assertThat(resolution.selection().ordinal()).as(phrase).isEqualTo(3);
            else if (phrase.contains("आखिरी")) assertThat(resolution.selection().ordinal()).as(phrase).isEqualTo(-1);
            else assertThat(resolution.selection().exactTime()).as(phrase).isEqualTo("21:00");
        }
        assertThat(page.displayOffset()).isEqualTo(6);
    }

    @Test
    void englishAndHinglishShowMoreAndNaturalTimePhrasesStayContextBound() {
        var resolver = new AivaV2DisplayedSlotSelectionResolver();
        for (String[] sample : new String[][]{
                {"en-US", "9 PM"}, {"en-US", "book 9 PM"}, {"en-US", "third one"},
                {"hi-IN", "9 baje wala book karo"}, {"hi-IN", "raat 9 baje"}}) {
            var turn = LanguageAdapterRegistry.defaults().resolve(sample[0], sample[1])
                    .normalize(sample[1], LocalDate.of(2026, 10, 26));
            var resolution = resolver.resolve(turn, lateEveningPage());
            assertThat(resolution.recognized()).as(sample[1]).isTrue();
            if (sample[1].equals("third one")) {
                assertThat(resolution.selection().ordinal()).isEqualTo(3);
            } else {
                assertThat(resolution.selection().exactTime()).as(sample[1]).isEqualTo("21:00");
            }
        }
    }

    private AvailabilityResult availability() {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");
        return new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1, "fingerprint", "provider",
                "doctor", "clinic", LocalDate.of(2026, 10, 27), null, null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("slot-1", LocalTime.of(9, 0), LocalTime.of(9, 30), "9:00 AM"),
                        new AvailabilitySlot("slot-2", LocalTime.of(9, 30), LocalTime.of(10, 0), "9:30 AM"),
                        new AvailabilitySlot("slot-3", LocalTime.of(10, 0), LocalTime.of(10, 30), "10:00 AM")),
                null, false, now, now.plusSeconds(300), 0, 3);
    }

    private AvailabilityResult availabilityAtSecondPage() {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");
        return new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1, "fingerprint", "provider",
                "doctor", "clinic", LocalDate.of(2026, 10, 27), "EVENING", null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("slot-a", LocalTime.of(17, 30), LocalTime.of(18, 0), "5:30 PM"),
                        new AvailabilitySlot("slot-b", LocalTime.of(18, 0), LocalTime.of(18, 30), "6:00 PM"),
                        new AvailabilitySlot("slot-c", LocalTime.of(18, 30), LocalTime.of(19, 0), "6:30 PM"),
                        new AvailabilitySlot("slot-1", LocalTime.of(18, 30), LocalTime.of(19, 0), "6:30 PM"),
                        new AvailabilitySlot("slot-2", LocalTime.of(19, 0), LocalTime.of(19, 30), "7:00 PM"),
                        new AvailabilitySlot("slot-3", LocalTime.of(19, 30), LocalTime.of(20, 0), "7:30 PM")),
                null, false, now, now.plusSeconds(300), 3, 3);
    }

    private AvailabilityResult lateEveningPage() {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-read-only", "doctor", "doctor",
                null, LocalDate.of(2026, 10, 27), "evening", null, "Asia/Kolkata",
                List.of(new AvailabilitySlot(null, LocalTime.of(17, 0), LocalTime.of(17, 30), "5 PM"),
                        new AvailabilitySlot(null, LocalTime.of(17, 30), LocalTime.of(18, 0), "5:30 PM"),
                        new AvailabilitySlot(null, LocalTime.of(18, 0), LocalTime.of(18, 30), "6 PM"),
                        new AvailabilitySlot(null, LocalTime.of(18, 30), LocalTime.of(19, 0), "6:30 PM"),
                        new AvailabilitySlot(null, LocalTime.of(19, 0), LocalTime.of(19, 30), "7 PM"),
                        new AvailabilitySlot(null, LocalTime.of(19, 30), LocalTime.of(20, 0), "7:30 PM"),
                        new AvailabilitySlot(null, LocalTime.of(20, 0), LocalTime.of(20, 30), "8 PM"),
                        new AvailabilitySlot(null, LocalTime.of(20, 30), LocalTime.of(21, 0), "8:30 PM"),
                        new AvailabilitySlot(null, LocalTime.of(21, 0), LocalTime.of(21, 30), "9 PM")),
                "VOICE_DAYPART:EVENING", true, now, now.plusSeconds(300), 6, 3);
    }
}
