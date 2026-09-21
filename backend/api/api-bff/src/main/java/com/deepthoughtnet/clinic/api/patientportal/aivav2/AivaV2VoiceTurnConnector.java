package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.UUID;

/** The narrow public boundary used by voice after STT has produced a final utterance. */
@Component
public class AivaV2VoiceTurnConnector {
    private final AivaV2ConversationService conversationService;
    private final LanguageAdapterRegistry languageAdapterRegistry;

    AivaV2VoiceTurnConnector(AivaV2ConversationService conversationService) {
        this(conversationService, LanguageAdapterRegistry.defaults());
    }

    @Autowired
    AivaV2VoiceTurnConnector(AivaV2ConversationService conversationService,
                             LanguageAdapterRegistry languageAdapterRegistry) {
        this.conversationService = conversationService;
        this.languageAdapterRegistry = languageAdapterRegistry;
    }

    public String responseLanguageForVoice(String transcript, String requestedLanguage) {
        var adapter = languageAdapterRegistry.resolve(requestedLanguage, transcript);
        return adapter.normalize(transcript).responseLanguage();
    }

    public long lastAcceptedVoiceTurnSequence(UUID tenantId, UUID patientId, String conversationId) {
        return conversationService.lastAcceptedVoiceTurnSequence(tenantId, patientId, conversationId);
    }

    public MessageResponse submitFinalTranscript(String conversationId,
                                                  String transcript,
                                                  String language,
                                                  String clientTurnId,
                                                  long clientTurnSequence) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("Voice conversation identifier is required.");
        }
        if (!StringUtils.hasText(transcript)) {
            throw new IllegalArgumentException("Final transcript is required.");
        }
        if (!StringUtils.hasText(clientTurnId) || clientTurnSequence < 1) {
            throw new IllegalArgumentException("A valid voice client turn is required.");
        }
        return conversationService.message(new MessageRequest(
                conversationId,
                transcript,
                language,
                null,
                clientTurnId,
                clientTurnSequence
        ));
    }
}
