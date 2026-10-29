package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.MDC;

/** Correlation metadata for one inbound Voice turn. No transcript or patient data is stored here. */
public final class AivaV2VoiceFlowTrace {
    public static final String TURN_ID = "voiceTurnId";
    public static final String TURN_NUMBER = "voiceTurnNumber";
    public static final String TENANT_ID = "voiceTenantId";
    public static final String CALL_ID = "voiceProviderCallId";
    public static final String CONVERSATION_HASH = "voiceConversationIdHash";

    private AivaV2VoiceFlowTrace() { }

    public static String turnId() {
        String id = MDC.get(TURN_ID);
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }

    public static String turnNumber() {
        String number = MDC.get(TURN_NUMBER);
        return number == null ? "unknown" : number;
    }

    public static String conversationHash(String value) {
        if (value == null || value.isBlank()) return "absent";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
