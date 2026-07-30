package com.deepthoughtnet.clinic.api.discover.provider.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProviderAuthModelsTest {
    @Test
    void loginChallengeResponseOmitsDevelopmentCodeWhenUnavailable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String json = objectMapper.writeValueAsString(new ProviderAuthModels.LoginChallengeResponse(
                "challenge-123",
                com.deepthoughtnet.clinic.discover.verification.VerificationChannel.SMS,
                "******1200",
                "Verification service is temporarily unavailable. Please try again later.",
                null,
                "PRODUCTION",
                java.time.OffsetDateTime.parse("2026-07-29T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-07-29T00:01:00Z"),
                300,
                60,
                null,
                null
        ));

        assertThat(json).doesNotContain("developmentCode");
        assertThat(json).contains("\"verificationMode\":\"PRODUCTION\"");
    }
}
