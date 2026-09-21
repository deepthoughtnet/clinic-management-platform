package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AivaV2LogRedactionTest {

    @Test
    void correlationValueDoesNotExposeSensitiveIdentifiers() {
        String sensitive = "PATIENT_SECRET_123|TENANT_SECRET_456|CONVERSATION_SECRET_789";

        String redacted = AivaV2LogRedaction.correlation(sensitive);

        assertThat(redacted).hasSize(16);
        assertThat(redacted).doesNotContain("PATIENT_SECRET_123", "TENANT_SECRET_456", "CONVERSATION_SECRET_789");
        assertThat(redacted).isEqualTo(AivaV2LogRedaction.correlation(sensitive));
        assertThat(redacted).isNotEqualTo(AivaV2LogRedaction.correlation("different-conversation"));
    }
}
