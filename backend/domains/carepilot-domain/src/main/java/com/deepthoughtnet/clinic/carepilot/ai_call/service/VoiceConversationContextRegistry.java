package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceInteractionType;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** In-flight correlation keyed exclusively by the server-issued provider call ID. */
@Component
public class VoiceConversationContextRegistry {
    private final ConcurrentMap<String, VoiceConversationContext> contexts = new ConcurrentHashMap<>();

    public boolean register(VoiceConversationContext context) {
        if (context == null || context.providerCallId() == null || context.providerCallId().isBlank()) return false;
        return contexts.putIfAbsent(context.providerCallId(), context) == null;
    }

    public Optional<VoiceConversationContext> find(String providerCallId) {
        return providerCallId == null ? Optional.empty() : Optional.ofNullable(contexts.get(providerCallId));
    }

    public Optional<VoiceConversationContext> remove(String providerCallId) {
        return providerCallId == null ? Optional.empty() : Optional.ofNullable(contexts.remove(providerCallId));
    }

    public int size() { return contexts.size(); }

    public record VoiceConversationContext(
            String providerCallId,
            UUID executionId,
            UUID tenantId,
            UUID patientId,
            VoiceInteractionType interactionType,
            String language,
            String promptId,
            int maxAdditionalAttempts
    ) {
        public VoiceConversationContext {
            maxAdditionalAttempts = Math.max(0, maxAdditionalAttempts);
        }
    }
}
