package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.UUID;

/** Trusted application context for healthcare AI tools; it is not an authentication context. */
public record HealthcareAiExecutionContext(
        UUID tenantId,
        UUID patientId,
        String conversationPartyType,
        HealthcareAiChannel channel,
        UUID interactionId,
        UUID clinicId,
        UUID locationId,
        String language
) {
    public HealthcareAiExecutionContext {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (channel == null) throw new IllegalArgumentException("channel is required");
        if (!"SINGLE_MATCH".equals(conversationPartyType)
                && patientId != null) {
            throw new IllegalArgumentException("patientId is only valid for SINGLE_MATCH");
        }
    }
}
