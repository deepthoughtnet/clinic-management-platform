package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;

interface AivaV2ConversationDecisionGateway {
    ConversationDecision decide(String text, String language, SessionProjection context);
}
