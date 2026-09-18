package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.AivaLanguageAdapter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DeterministicControl;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AivaLanguageAdapterTest {
    private final LanguageAdapterRegistry registry = LanguageAdapterRegistry.defaults();

    @Test
    void englishAndHindiConfirmationControlsHaveEquivalentCanonicalText() {
        assertThat(normalize("no", "en").normalizedText()).isEqualTo(normalize("nahi", "hi").normalizedText());
        assertThat(normalize("no", "en").normalizedText()).isEqualTo(normalize("नहीं", "hi").normalizedText());
        assertThat(normalize("yes", "en").normalizedText()).isEqualTo(normalize("haan", "hi").normalizedText());
        assertThat(normalize("yes", "en").normalizedText()).isEqualTo(normalize("हाँ", "hi").normalizedText());
    }

    @Test
    void boundedHinglishAffirmativesNormalizeToPositiveButKarDoAloneDoesNot() {
        for (String phrase : new String[]{"haan", "haan kar do", "theek hai", "theek hai kar do",
                "thik hai", "thik hai kar do"}) {
            assertThat(normalize(phrase, "hi").confirmation()).as(phrase)
                    .isEqualTo(NormalizedUserTurn.Confirmation.POSITIVE);
        }
        assertThat(normalize("kar do", "hi").confirmation())
                .isEqualTo(NormalizedUserTurn.Confirmation.NONE);
        for (String phrase : new String[]{"nahi", "nahin", "नहीं"}) {
            assertThat(normalize(phrase, "hi").confirmation()).as(phrase)
                    .isEqualTo(NormalizedUserTurn.Confirmation.NEGATIVE);
        }
    }

    @Test
    void hindiDevanagariAndHinglishNormalizeDaypartsAndPagination() {
        for (String text : new String[]{"shaam ki slots dikhao", "शाम की स्लॉट्स दिखाओ"}) {
            assertThat(normalize(text, "hi").normalizedText()).contains("evening", "slot", "show");
        }
        assertThat(normalize("subah ki slots dikhao", "hi").normalizedText()).contains("morning");
        assertThat(normalize("aur slots dikhao", "hi").normalizedText()).contains("more", "slot", "show");
        assertThat(normalize("दूसरी स्लॉट्स दिखाओ", "hi").normalizedText()).contains("next", "slot", "show");
    }

    @Test
    void hindiExactTimeFormsShareCanonicalSelectionText() {
        assertThat(normalize("20:30 works", "en").selectionText())
                .isEqualTo(normalize("20:30 वाली ठीक है", "hi").selectionText());
        assertThat(normalize("रात 8 बजे वाला ठीक है", "hi").selectionText()).isEqualTo("8 pm works");
    }

    @Test
    void embeddedExactClockTimeIsTypedWithoutMistakingTimeBoundsForSelection() {
        assertThat(normalize("Cancel my appointment with Dr Akshu at 20:00", "en").exactTime())
                .isEqualTo(LocalTime.of(20, 0));
        assertThat(normalize("डॉ. अक्षु के साथ 24 सितंबर को 20:30 बजे की अपॉइंटमेंट रद्द करें", "hi").exactTime())
                .isEqualTo(LocalTime.of(20, 30));
        assertThat(normalize("Do you have appointments after 20:00?", "en").exactTime()).isNull();
        assertThat(normalize("Between 19:00 and 20:00", "en").exactTime()).isNull();
    }

    @Test
    void mixedHindiPreservesDoctorEntityWithoutTranslatingIt() {
        NormalizedUserTurn turn = normalize("Mujhe Dr Akshu ke saath appointment book karni hai", "hi");

        assertThat(turn.explicitDoctorReference()).isEqualTo("Dr Akshu");
        assertThat(turn.rawText()).contains("Akshu");
    }

    @Test
    void mixedScriptHindiConjunctionPreservesLatinDoctorEntity() {
        NormalizedUserTurn turn = normalize(
                "Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:00 बजे की अपॉइंटमेंट रद्द करें", "hi");

        assertThat(turn.doctorEntity()).isNotNull();
        assertThat(turn.doctorEntity().canonicalQuery()).isEqualTo("Doc Akshu Kumar");
        assertThat(turn.doctorEntity().rawSpan()).isEqualTo("Doc Akshu Kumar");
        assertThat(turn.temporal().localDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(turn.exactTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void devanagariDoctorReferencesBecomeCanonicalProviderText() {
        for (String text : new String[]{"डॉ. अक्षु", "डॉक्टर अक्षु", "डॉ. अक्षु कुमार"}) {
            NormalizedUserTurn turn = normalize(text + " के साथ अपॉइंटमेंट", "hi");

            assertThat(turn.explicitDoctorReference()).as(text).startsWith("Dr Akshu");
        }
        assertThat(normalize("डॉ. शर्मा के साथ अपॉइंटमेंट", "hi").explicitDoctorReference())
                .startsWith("Dr Shar");
        assertThat(normalize("डॉक्टर मेहता के साथ अपॉइंटमेंट", "hi").explicitDoctorReference())
                .startsWith("Dr Meh");
    }

    @Test
    void responseLanguageAndStyleFollowCurrentTurnScript() {
        assertThat(normalize("शाम की स्लॉट्स दिखाओ", "hi").responseLanguage()).isEqualTo("hi");
        assertThat(normalize("शाम की स्लॉट्स दिखाओ", "hi").responseStyle())
                .isEqualTo(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD);
        assertThat(normalize("shaam ki slots dikhao", "hi").responseStyle())
                .isEqualTo(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH);
        assertThat(normalize("show evening slots", "en").responseLanguage()).isEqualTo("en");
    }

    @Test
    void devanagariBookingTermsNormalizeToCanonicalIntentWords() {
        NormalizedUserTurn turn = normalize("मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi");

        assertThat(turn.normalizedText()).contains("appointment", "book");
        assertThat(turn.controls()).isEmpty();
    }

    @Test
    void registrySelectsHindiForScriptOrBoundedHindiSignals() {
        assertThat(registry.resolve("hi", "हाँ")).isInstanceOf(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.HindiLanguageAdapter.class);
        assertThat(registry.resolve(null, "shaam ki slots dikhao").languageCode()).isEqualTo("hi");
        assertThat(registry.resolve("en", "show more slots").languageCode()).isEqualTo("en");
    }

    @Test
    void equivalentEnglishHinglishAndHindiDatesResolveBeforeTheGateway() {
        LocalDate reference = LocalDate.of(2026, 9, 1);
        for (var sample : new String[][]{{"en", "24 September"}, {"hi", "24 September"}, {"hi", "24 सितंबर"},
                {"hi", "24 सितंबर की स्लॉट्स दिखाओ"}, {"hi", "24 September ki slots dikhao"}}) {
            NormalizedUserTurn turn = registry.resolve(sample[0], sample[1]).normalize(sample[1], reference);
            assertThat(turn.temporal().localDate()).as(sample[1]).isEqualTo(LocalDate.of(2026, 9, 24));
            assertThat(turn.temporal().yearInferred()).as(sample[1]).isTrue();
            assertThat(turn.temporal().rawSpan()).as(sample[1]).contains("24");
        }
    }

    @Test
    void certifiesAlreadySupportedRelativeWeekdayMonthAndDaypartEquivalents() {
        LocalDate reference = LocalDate.of(2026, 9, 1);
        for (String[] form : new String[][]{{"en", "today"}, {"hi", "aaj"}, {"hi", "आज"}}) {
            assertThat(registry.resolve(form[0], form[1]).normalize(form[1], reference).temporal().localDate())
                    .as(form[1]).isEqualTo(reference);
        }
        for (String[] form : new String[][]{{"en", "tomorrow"}, {"hi", "kal"}, {"hi", "कल"}}) {
            assertThat(registry.resolve(form[0], form[1]).normalize(form[1], reference).temporal().localDate())
                    .as(form[1]).isEqualTo(reference.plusDays(1));
        }
        for (String[] form : new String[][]{{"en", "Monday"}, {"hi", "somvaar"}, {"hi", "सोमवार"}}) {
            assertThat(registry.resolve(form[0], form[1]).normalize(form[1], reference).temporal().localDate())
                    .as(form[1]).isEqualTo(LocalDate.of(2026, 9, 7));
        }
        for (String[] form : new String[][]{{"en", "24 September 2026"}, {"hi", "24 September 2026"},
                {"hi", "24 सितंबर 2026"}}) {
            assertThat(registry.resolve(form[0], form[1]).normalize(form[1], reference).temporal().localDate())
                    .as(form[1]).isEqualTo(LocalDate.of(2026, 9, 24));
        }
        for (String[] form : new String[][]{{"en", "morning", "MORNING"}, {"hi", "subah", "MORNING"},
                {"hi", "सुबह", "MORNING"}, {"en", "afternoon", "AFTERNOON"},
                {"hi", "dopahar", "AFTERNOON"}, {"hi", "दोपहर", "AFTERNOON"},
                {"en", "evening", "EVENING"}, {"hi", "shaam", "EVENING"}, {"hi", "शाम", "EVENING"}}) {
            assertThat(registry.resolve(form[0], form[1]).normalize(form[1], reference).daypart().name())
                    .as(form[1]).isEqualTo(form[2]);
        }
    }

    @Test
    void multilingualDeterministicInputsProduceTypedCanonicalControls() {
        for (String text : new String[]{"yes", "haan", "हाँ"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.confirmation()).as(text).isEqualTo(NormalizedUserTurn.Confirmation.POSITIVE);
        }
        for (String text : new String[]{"no", "nahi", "नहीं"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.confirmation()).as(text).isEqualTo(NormalizedUserTurn.Confirmation.NEGATIVE);
        }
        for (String text : new String[]{"evening", "shaam", "शाम"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.daypart()).as(text).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
        }
        for (String text : new String[]{"show more slots", "aur slots dikhao", "और स्लॉट्स दिखाओ"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.pagination()).as(text).isEqualTo(NormalizedUserTurn.Pagination.SHOW_MORE);
        }
        for (String text : new String[]{"20:30 works", "20:30 wali theek hai", "20:30 वाली ठीक है"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.exactTime()).as(text).isEqualTo(LocalTime.of(20, 30));
        }
        for (String text : new String[]{"first", "pehla", "पहला"}) {
            var turn = registry.resolve("", text).normalize(text, LocalDate.of(2026, 9, 1));
            assertThat(turn.ordinal()).as(text).isEqualTo(1);
        }
    }

    @Test
    void bookingTripletRetainsRawDoctorAndCanonicalProviderQuery() {
        for (String[] sample : new String[][]{{"en", "I want to book with Dr Akshu"},
                {"hi", "Mujhe Dr Akshu ke saath appointment book karni hai"},
                {"hi", "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है"}}) {
            var turn = registry.resolve(sample[0], sample[1]).normalize(sample[1], LocalDate.of(2026, 9, 1));
            assertThat(turn.intent()).isEqualTo(NormalizedUserTurn.Intent.BOOKING);
            assertThat(turn.doctorEntity()).isNotNull();
            assertThat(turn.doctorEntity().rawSpan()).isNotBlank();
            assertThat(turn.doctorEntity().canonicalQuery()).isEqualTo("Dr Akshu");
            assertThat(turn.canonicalSemanticText()).isEqualTo("book appointment");
        }
    }

    @Test
    void numericOnlyTurnsRetainConversationPresentationMetadataOutsideDomainState() {
        var store = new AivaV2SessionStore(java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-01T00:00:00Z"), java.time.ZoneOffset.UTC));
        var expiry = java.time.Instant.parse("2026-09-01T00:30:00Z");
        var hindi = registry.resolve("hi", "मुझे अपॉइंटमेंट बुक करनी है")
                .normalize("मुझे अपॉइंटमेंट बुक करनी है", LocalDate.of(2026, 9, 1));
        var devanagariMetadata = store.updatePresentation("thread-hi", hindi, expiry);
        var numeric = registry.resolve("en", "20:00").normalize("20:00", LocalDate.of(2026, 9, 1));
        var afterNumeric = store.updatePresentation("thread-hi", numeric, expiry);
        assertThat(afterNumeric.responseLanguage()).isEqualTo("hi");
        assertThat(afterNumeric.responseStyle()).isEqualTo(devanagariMetadata.responseStyle());

        var hinglish = registry.resolve("hi", "Mujhe appointment book karni hai")
                .normalize("Mujhe appointment book karni hai", LocalDate.of(2026, 9, 1));
        store.updatePresentation("thread-hinglish", hinglish, expiry);
        var afterHinglishNumeric = store.updatePresentation("thread-hinglish", numeric, expiry);
        assertThat(afterHinglishNumeric.responseLanguage()).isEqualTo("hi");
        assertThat(afterHinglishNumeric.responseStyle())
                .isEqualTo(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH);
    }

    @Test
    void compatibleCompoundControlsHaveCanonicalOrderAcrossLanguages() {
        assertThat(normalize("no, show me other slots", "en").controls())
                .containsExactly(DeterministicControl.CONFIRMATION_NEGATIVE,
                        DeterministicControl.SHOW_MORE_SLOTS);
        assertThat(normalize("nahi, dusri slots dikhao", "hi").controls())
                .containsExactly(DeterministicControl.CONFIRMATION_NEGATIVE,
                        DeterministicControl.SHOW_MORE_SLOTS);
        assertThat(normalize("नहीं, दूसरी स्लॉट्स दिखाओ", "hi").controls())
                .containsExactly(DeterministicControl.CONFIRMATION_NEGATIVE,
                        DeterministicControl.SHOW_MORE_SLOTS);
    }

    private NormalizedUserTurn normalize(String text, String language) {
        AivaLanguageAdapter adapter = registry.resolve(language, text);
        return adapter.normalize(text);
    }
}
