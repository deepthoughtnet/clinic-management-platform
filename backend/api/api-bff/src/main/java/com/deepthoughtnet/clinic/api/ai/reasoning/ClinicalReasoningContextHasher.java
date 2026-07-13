package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class ClinicalReasoningContextHasher {
    private final ClinicalReasoningPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    ClinicalReasoningContextHasher(ClinicalReasoningPromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    String contextHash(UUID tenantId,
                       ConsultationEntity consultation,
                       ClinicalContextResponse context,
                       ClinicalReasoningRequest request) {
        try {
            Map<String, Object> input = new LinkedHashMap<>(promptBuilder.buildInput(tenantId, consultation, context, request, false, null));
            input.remove("reasoningPrompt");
            input.remove("repairMode");
            input.remove("repairReason");
            input.remove("strictJsonOnly");
            return sha256(objectMapper.writeValueAsString(input));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to hash clinical reasoning context", ex);
        }
    }

    String snapshotKey(String contextHash, String promptVersion, String reasoningEngineVersion) {
        return sha256(String.join("|", safe(contextHash), safe(promptVersion), safe(reasoningEngineVersion)));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
