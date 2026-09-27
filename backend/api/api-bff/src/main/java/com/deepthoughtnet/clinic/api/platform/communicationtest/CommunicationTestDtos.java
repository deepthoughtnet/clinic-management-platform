package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CommunicationTestDtos {
    private CommunicationTestDtos() {}

    public enum TestMode { PROVIDER_ONLY, REMINDER_SIMULATION }

    public enum Scenario { APPOINTMENT_REMINDER, FOLLOW_UP_CONSULTATION, MEDICATION_REFILL_REVIEW, LAB_REMINDER, LAB_REPORT_READY }

    public record TestRequest(
            @NotBlank String recipient,
            String subject,
            @NotBlank String message,
            String language,
            TestMode mode,
            Scenario scenario,
            String provider
    ) {
        public TestRequest(String recipient, String subject, String message, String language, TestMode mode, Scenario scenario) {
            this(recipient, subject, message, language, mode, scenario, null);
        }
    }

    public record HealthResponse(List<ChannelHealth> channels, String correlationId, List<CapabilityHealth> capabilities) {
        public HealthResponse(List<ChannelHealth> channels, String correlationId) {
            this(channels, correlationId, List.of());
        }
    }

    public record CapabilityHealth(String capability, String provider, String status, String message) {}

    public record ChannelHealth(
            String channel,
            String provider,
            String status,
            String message,
            boolean configured,
            boolean providerAvailable,
            boolean supportsTest,
            String senderIdentity,
            List<String> missingConfigurationKeys,
            OffsetDateTime checkedAt
    ) {}

    public record Result(
            String requestId,
            String correlationId,
            String channel,
            String provider,
            String providerRequestId,
            String status,
            boolean success,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String failureCategory,
            String failureMessage
    ) {}
}
