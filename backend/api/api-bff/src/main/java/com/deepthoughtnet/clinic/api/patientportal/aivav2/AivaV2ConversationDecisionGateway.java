package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;

interface AivaV2ConversationDecisionGateway {
    ConversationDecision decide(String text, String language, SessionProjection context);

    default NormalizedUserTurn normalizeTurn(String text, String language, LocalDate referenceDate) {
        return presentationTurn(text, language);
    }

    default ConversationDecision decide(NormalizedUserTurn turn, SessionProjection context) {
        return decide(turn.rawText(), turn.languageCode(), context);
    }

    default NormalizedUserTurn presentationTurn(String text, String language) {
        return new NormalizedUserTurn(text, text, text, language, null, false,
                java.util.List.of(), language, ResponseStyle.STANDARD);
    }
}
