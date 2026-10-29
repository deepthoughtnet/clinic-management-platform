package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Explicit channel context for AIVA execution; this is not an authentication context. */
public record AivaExecutionContext(
        UUID tenantId,
        UUID identifiedPatientId,
        String channel,
        String language,
        ZoneId timezone,
        String sessionId,
        UUID interactionId,
        UUID clinicId,
        UUID locationId,
        Set<String> capabilities,
        String providerCallId,
        String correlationId,
        AivaTransactionAuthorization transactionAuthorization
) {
    public AivaExecutionContext {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        timezone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /** Compatibility constructor; the patient identifier is identification context only. */
    public AivaExecutionContext(UUID tenantId, UUID identifiedPatientId, String channel, String language,
                                ZoneId timezone, String sessionId, UUID interactionId, UUID clinicId,
                                UUID locationId, Set<String> capabilities) {
        this(tenantId, identifiedPatientId, channel, language, timezone, sessionId, interactionId,
                clinicId, locationId, capabilities, "VOICE".equalsIgnoreCase(channel) ? sessionId : null,
                null, AivaTransactionAuthorization.unauthenticated(tenantId, sessionId, identifiedPatientId));
    }

    /** Alias retained for existing callers; this is never an authenticated patient identity. */
    @Deprecated(forRemoval = false)
    public UUID patientId() {
        return identifiedPatientId;
    }

    public String effectiveSessionBinding() {
        return providerCallId == null || providerCallId.isBlank() ? sessionId : providerCallId;
    }
}
