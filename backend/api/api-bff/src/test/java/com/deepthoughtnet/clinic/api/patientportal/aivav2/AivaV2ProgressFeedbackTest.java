package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import org.junit.jupiter.api.Test;

class AivaV2ProgressFeedbackTest {
    @Test
    void mapsAuthoritativeOperationsToShortDeterministicEnglishCopy() {
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.RESOLVE_PROVIDER, "en", ResponseStyle.STANDARD))
                .extracting(AivaV2ProgressEvent::acknowledgement)
                .isEqualTo("Let me check available doctors.");
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.GET_AVAILABILITY, "en", ResponseStyle.STANDARD))
                .extracting(AivaV2ProgressEvent::acknowledgement)
                .isEqualTo("I’m checking available slots.");
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.CONFIRM_BOOKING, "en", ResponseStyle.STANDARD))
                .extracting(AivaV2ProgressEvent::acknowledgement)
                .isEqualTo("Let me confirm those appointment details.");
    }

    @Test
    void usesHindiAndHinglishWithoutCallingSemanticTranslation() {
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.GET_AVAILABILITY, "hi-IN", ResponseStyle.STANDARD))
                .extracting(AivaV2ProgressEvent::acknowledgement)
                .isEqualTo("मैं उपलब्ध स्लॉट देख रही हूँ।");
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.GET_AVAILABILITY, "hi-IN", ResponseStyle.HINGLISH))
                .extracting(AivaV2ProgressEvent::acknowledgement)
                .isEqualTo("Main available slots dekh rahi hoon.");
    }

    @Test
    void doesNotEmitProgressForPresentationOnlyOrNonAuthoritativeOperations() {
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.INTRO, "en", ResponseStyle.STANDARD)).isNull();
        assertThat(AivaV2ProgressFeedback.forOperation(Operation.UNKNOWN, "en", ResponseStyle.STANDARD)).isNull();
    }

    @Test
    void genericSemanticProgressIsLocalizedAndOperationNeutral() {
        assertThat(AivaV2ProgressFeedback.generic("en", ResponseStyle.STANDARD).acknowledgement())
                .isEqualTo("One moment while I check.");
        assertThat(AivaV2ProgressFeedback.generic("hi-IN", ResponseStyle.STANDARD).acknowledgement())
                .contains("एक क्षण");
        assertThat(AivaV2ProgressFeedback.generic("hi-IN", ResponseStyle.HINGLISH).acknowledgement())
                .contains("Ek pal");
    }
}
