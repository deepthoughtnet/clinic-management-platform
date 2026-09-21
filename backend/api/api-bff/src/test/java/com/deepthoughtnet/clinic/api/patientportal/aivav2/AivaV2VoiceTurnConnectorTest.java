package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AivaV2VoiceTurnConnectorTest {
    @Test
    void forwardsOneFinalizedTurnWithStableConversationAndTurnMetadata() {
        AivaV2ConversationService service = mock(AivaV2ConversationService.class);
        MessageResponse expected = new MessageResponse("voice-conversation", "turn-1", "ठीक है", "LOOKUP_RESULT",
                null, "GEMINI", false, List.of());
        when(service.message(any(MessageRequest.class))).thenReturn(expected);
        AivaV2VoiceTurnConnector connector = new AivaV2VoiceTurnConnector(service);

        MessageResponse actual = connector.submitFinalTranscript(
                "voice-conversation", "show my appointments", "hi-in", "voice-turn-1", 1L);

        assertThat(actual).isSameAs(expected);
        verify(service).message(new MessageRequest(
                "voice-conversation", "show my appointments", "hi-in", null, "voice-turn-1", 1L));
    }

    @Test
    void resolvesVoiceLocaleFromTheCurrentFinalTranscriptWhenLanguageIsAuto() {
        AivaV2VoiceTurnConnector connector = new AivaV2VoiceTurnConnector(mock(AivaV2ConversationService.class));

        assertThat(connector.responseLanguageForVoice("Show my appointments", "auto")).isEqualTo("en");
        assertThat(connector.responseLanguageForVoice("Meri appointments dikhao", "auto")).isEqualTo("hi");
        assertThat(connector.responseLanguageForVoice("मेरी अपॉइंटमेंट दिखाओ", "auto")).isEqualTo("hi");
    }
}
