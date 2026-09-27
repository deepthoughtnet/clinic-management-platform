package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotVoiceVoiceCallProviderTest {
    @Test
    void incompleteConfigurationIsNotReady() {
        DotVoiceProperties properties = properties();
        properties.setApiKey("");
        DotVoiceVoiceCallProvider provider = new DotVoiceVoiceCallProvider(properties, (p, to, script) -> null);

        assertFalse(provider.isReady());
        assertEquals(VoiceCallStatus.FAILED, provider.placeCall(request()).status());
    }

    @Test
    void stageOneRuntimeConfigurationIsReadyWithoutCallbackOrMedia() {
        DotVoiceProperties properties = new DotVoiceProperties();
        properties.setEnabled(true);
        properties.setApiKey("runtime-secret");
        properties.setBaseUrl("http://rec.mindserv.co:8000");
        properties.setCallsPath("/v1/calls");
        properties.setFromNumber("7969812868");
        properties.setTrunk("trunk2");
        properties.setTimeoutMs(10_000);
        properties.setCallbackBaseUrl("");

        DotVoiceVoiceCallProvider provider = new DotVoiceVoiceCallProvider(properties, (p, to, script) -> null);

        assertTrue(provider.isReady());
    }

    @Test
    void initiatedCallMapsProviderIdAndQueuedStatus() {
        DotVoiceProperties properties = properties();
        DotVoiceVoiceCallProvider provider = new DotVoiceVoiceCallProvider(properties,
                (p, to, script) -> new DotVoiceHttpResponse(200, "{\"call_id\":\"call-123\",\"status\":\"initiated\"}"));

        var result = provider.placeCall(request());

        assertTrue(provider.isReady());
        assertEquals("dotvoice", result.providerName());
        assertEquals("call-123", result.providerCallId());
        assertEquals(VoiceCallStatus.QUEUED, result.status());
    }

    @Test
    void providerStatusesAreNormalized() {
        DotVoiceProperties properties = properties();
        DotVoiceVoiceCallProvider provider = new DotVoiceVoiceCallProvider(properties,
                (p, to, script) -> new DotVoiceHttpResponse(200, "{\"call_id\":\"call-1\",\"status\":\"no-answer\"}"));

        assertEquals(VoiceCallStatus.NO_ANSWER, provider.placeCall(request()).status());
    }

    @Test
    void authenticationAndTimeoutFailuresAreSafe() {
        DotVoiceProperties properties = properties();
        DotVoiceVoiceCallProvider authProvider = new DotVoiceVoiceCallProvider(properties,
                (p, to, script) -> new DotVoiceHttpResponse(401, "{\"error\":\"secret\"}"));
        DotVoiceVoiceCallProvider timeoutProvider = new DotVoiceVoiceCallProvider(properties,
                (p, to, script) -> new DotVoiceHttpResponse(599, null));

        assertEquals("AUTHENTICATION_FAILED", authProvider.placeCall(request()).failureReason());
        assertEquals("TIMEOUT_OR_TRANSPORT_ERROR", timeoutProvider.placeCall(request()).failureReason());
    }

    @Test
    void successfulHttpResponseWithoutCallIdIsNotReportedAsSuccess() {
        DotVoiceProperties properties = properties();
        DotVoiceVoiceCallProvider provider = new DotVoiceVoiceCallProvider(properties,
                (p, to, script) -> new DotVoiceHttpResponse(200, "{\"status\":\"initiated\"}"));

        var result = provider.placeCall(request());

        assertEquals(VoiceCallStatus.FAILED, result.status());
        assertEquals("INVALID_PROVIDER_RESPONSE", result.failureReason());
    }

    private DotVoiceProperties properties() {
        DotVoiceProperties properties = new DotVoiceProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://dotvoice.test");
        properties.setFromNumber("+917969812868");
        properties.setTrunk("trunk2");
        return properties;
    }

    private VoiceCallRequest request() {
        return new VoiceCallRequest(UUID.randomUUID(), null, UUID.randomUUID(), "+919876543210", "Connectivity test", OffsetDateTime.now(),
                Map.of());
    }
}
