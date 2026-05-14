package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallTranscript;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Safe default provider for local/UAT environments.
 * Simulates provider behavior without telephony side effects or vendor lock-in.
 */
@Component
public class MockVoiceCallProvider implements VoiceCallProvider {
    private final boolean enabled;
    private final boolean failCalls;

    public MockVoiceCallProvider(
            @Value("${carepilot.voice.mock.enabled:true}") boolean enabled,
            @Value("${carepilot.voice.mock.force-failure:false}") boolean failCalls
    ) {
        this.enabled = enabled;
        this.failCalls = failCalls;
    }

    @Override
    public String providerName() {
        return "mock-voice";
    }

    @Override
    public boolean isReady() {
        return enabled;
    }

    @Override
    public VoiceCallResult placeCall(VoiceCallRequest request) {
        OffsetDateTime started = OffsetDateTime.now();
        OffsetDateTime ended = started.plusSeconds(45);
        if (failCalls) {
            return new VoiceCallResult(
                    VoiceCallStatus.FAILED,
                    providerName(),
                    "mock-" + UUID.randomUUID(),
                    "Simulated provider failure",
                    started,
                    ended,
                    null
            );
        }
        VoiceCallTranscript transcript = new VoiceCallTranscript(
                "Hello, this is CarePilot calling with an update.",
                "Call completed successfully.",
                "NEUTRAL",
                "ACKNOWLEDGED",
                false
        );
        return new VoiceCallResult(
                VoiceCallStatus.COMPLETED,
                providerName(),
                "mock-" + UUID.randomUUID(),
                null,
                started,
                ended,
                transcript
        );
    }
}
