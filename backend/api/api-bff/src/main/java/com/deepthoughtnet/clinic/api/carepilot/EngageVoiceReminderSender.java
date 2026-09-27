package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos;
import com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestService;
import com.deepthoughtnet.clinic.carepilot.messaging.service.ReminderCommunicationRequest;
import com.deepthoughtnet.clinic.carepilot.messaging.service.VoiceReminderSender;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/** Adapts Engage voice executions to the already-certified DotVoice reminder path. */
@Service
public class EngageVoiceReminderSender implements VoiceReminderSender {
    private final CommunicationTestService communicationTestService;

    public EngageVoiceReminderSender(CommunicationTestService communicationTestService) {
        this.communicationTestService = communicationTestService;
    }

    @Override
    public MessageResult send(ReminderCommunicationRequest request) {
        CommunicationTestDtos.Result result = communicationTestService.voice(
                request.tenantId(),
                new CommunicationTestDtos.TestRequest(
                        request.destination(), null, request.message(), request.language(),
                        CommunicationTestDtos.TestMode.REMINDER_SIMULATION,
                        scenario(request.scenario()), "dotvoice"),
                request.executionId().toString());
        boolean success = result.success();
        MessageDeliveryStatus status = success ? MessageDeliveryStatus.SENT : mapFailure(result.failureCategory());
        return new MessageResult(success, status, result.provider(), result.providerRequestId(),
                success ? null : result.failureCategory(), success ? null : result.failureMessage(),
                success ? (result.completedAt() == null ? OffsetDateTime.now() : result.completedAt()) : null);
    }

    private MessageDeliveryStatus mapFailure(String category) {
        if ("NOT_CONFIGURED".equalsIgnoreCase(category) || "PLAYBACK_NOT_CONFIGURED".equalsIgnoreCase(category)) {
            return MessageDeliveryStatus.NOT_CONFIGURED;
        }
        if ("STREAM_READY_NOT_RECEIVED".equalsIgnoreCase(category)) {
            return MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE;
        }
        return MessageDeliveryStatus.FAILED;
    }

    private CommunicationTestDtos.Scenario scenario(String value) {
        if (value == null || value.isBlank()) return CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER;
        try {
            return CommunicationTestDtos.Scenario.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER;
        }
    }
}
