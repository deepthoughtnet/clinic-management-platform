package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.Result;
import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.HealthResponse;
import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.TestRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

class CommunicationTestControllerTest {
    private final CommunicationTestService service = mock(CommunicationTestService.class);
    private final CommunicationTestController controller = new CommunicationTestController(service);

    @BeforeEach
    void setPlatformContext() {
        RequestContextHolder.set(new RequestContext(null, UUID.randomUUID(), "platform-admin",
                Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "corr-platform"));
    }

    @AfterEach
    void clearContext() { RequestContextHolder.clear(); }

    @Test
    void emailProviderOnlyPassesNullTenantInPlatformMode() {
        TestRequest request = new TestRequest("patient@example.com", "Subject", "Body", "en-IN", null, null, "msg91-email-smtp");
        Result expected = new Result("request-1", "corr-platform", "EMAIL", "msg91-email-smtp", "provider-1",
                "SENT", true, null, null, null, null);
        when(service.email(isNull(), any(TestRequest.class), org.mockito.ArgumentMatchers.eq("corr-platform"))).thenReturn(expected);

        Result result = controller.email(request);

        assertEquals(expected, result);
        verify(service).email(isNull(), org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.eq("corr-platform"));
    }

    @Test
    void voiceAndWhatsAppProviderOnlyAlsoPassNullTenantInPlatformMode() {
        TestRequest request = new TestRequest("+919876543210", null, "Connectivity test", "en-IN", null, null, null);
        Result expected = new Result("request-1", "corr-platform", "VOICE", "dotvoice", "call-1",
                "QUEUED", true, null, null, null, null);
        when(service.voice(isNull(), any(TestRequest.class), org.mockito.ArgumentMatchers.eq("corr-platform"))).thenReturn(expected);
        when(service.whatsapp(isNull(), any(TestRequest.class), org.mockito.ArgumentMatchers.eq("corr-platform"))).thenReturn(expected);

        assertEquals(expected, controller.voice(request));
        assertEquals(expected, controller.whatsapp(request));
        verify(service).voice(isNull(), org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.eq("corr-platform"));
        verify(service).whatsapp(isNull(), org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.eq("corr-platform"));
    }

    @Test
    void healthDoesNotRequireTenantInPlatformMode() {
        HealthResponse expected = new HealthResponse(java.util.List.of(), "corr-platform");
        when(service.health("corr-platform")).thenReturn(expected);

        assertEquals(expected, controller.health());
        verify(service).health("corr-platform");
    }

    @Test
    void inboundMediaIsIsolatedByProviderCallIdAndConvertedBeforeCompletion() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        Result callA = new Result("test-a", "corr-platform", "VOICE", "dotvoice", "call-a",
                "INITIATED", true, null, null, null, null);
        when(inboundService.voiceResultByProviderCallId("call-a")).thenReturn(callA);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getVad().setSilenceTimeoutMs(25);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(inboundService, properties);

        handler.onStreamStart("call-a");
        verify(inboundService, timeout(2_000)).markVoiceAnswered("call-a");
        handler.onPlaybackComplete("call-a");
        verify(inboundService, timeout(2_000)).onInteractivePlaybackComplete("call-a");
        handler.onMedia("call-a", new byte[]{0, 0});
        handler.onMedia("call-b", new byte[]{0});
        try { Thread.sleep(250); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }

        verify(inboundService, timeout(2_000)).processLiveUtterance(
                org.mockito.ArgumentMatchers.eq("call-a"),
                org.mockito.ArgumentMatchers.argThat(wav -> wav != null
                        && wav.length == 48
                        && wav[0] == 'R' && wav[1] == 'I' && wav[2] == 'F' && wav[3] == 'F'));
        verify(inboundService, org.mockito.Mockito.never()).processLiveUtterance(
                org.mockito.ArgumentMatchers.eq("call-b"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void threeSequentialInboundCallsCreateAndRemoveIndependentContexts() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        when(inboundService.inboundVoiceResult(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new CommunicationTestDtos.InboundVoiceResult(
                        invocation.getArgument(0), "caller", "did", "INBOUND", "RINGING", "hi-IN", "Sarvam",
                        null, null, null, null, null, null, "CONNECTED", null, null, null, null, null, null,
                        "RESOLVED", null));
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(
                inboundService, new VoiceTestProperties());

        for (String callId : new String[]{"call-a", "call-b", "call-c"}) {
            handler.onIncomingCall(callId, "caller-" + callId, "did");
            handler.onStreamStart(callId);
            handler.onPlaybackComplete(callId);
            handler.onStreamStop(callId, "completed");
        }

        verify(inboundService, timeout(2_000).times(3)).registerInboundCall(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("did"));
        verify(inboundService, timeout(2_000).times(3)).markInboundAnswered(org.mockito.ArgumentMatchers.anyString());
        verify(inboundService, timeout(2_000).times(3)).startInboundGreeting(org.mockito.ArgumentMatchers.anyString());
        verify(inboundService, timeout(2_000).times(3)).markInboundListening(org.mockito.ArgumentMatchers.anyString());
        verify(inboundService, timeout(2_000).times(3)).onInboundStreamStopped(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("completed"));
    }

    @Test
    void unknownDidDoesNotEnterGreetingOrTranscriptFlow() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        when(inboundService.inboundVoiceResult("unknown-call")).thenReturn(new CommunicationTestDtos.InboundVoiceResult(
                "unknown-call", "caller", "+919999999999", "INBOUND", "ROUTING_FAILED", "hi-IN", "Sarvam",
                null, null, null, null, null, null, "CONNECTED", null, null, null, null, null, null,
                "ROUTING_FAILED", "UNKNOWN_DID"));
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(
                inboundService, new VoiceTestProperties());

        handler.onIncomingCall("unknown-call", "caller", "+919999999999");
        handler.onStreamStart("unknown-call");

        verify(inboundService, timeout(2_000)).registerInboundCall("unknown-call", "caller", "+919999999999");
        verify(inboundService, org.mockito.Mockito.never()).markInboundAnswered("unknown-call");
        verify(inboundService, org.mockito.Mockito.never()).startInboundGreeting("unknown-call");
    }

    @Test
    void resolvedInboundSpeechIsObservedAfterGreetingAndDispatchedOnce() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        when(inboundService.inboundVoiceResult("speech-call")).thenReturn(new CommunicationTestDtos.InboundVoiceResult(
                "speech-call", "caller", "did", "INBOUND", "RINGING", "hi-IN", "Sarvam",
                null, null, null, null, null, null, "CONNECTED", null, null, null, null, null, null,
                "RESOLVED", null));
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getVad().setMinSpeechMs(20);
        properties.getVad().setSilenceTimeoutMs(25);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(inboundService, properties);

        handler.onIncomingCall("speech-call", "caller", "did");
        handler.onStreamStart("speech-call");
        verify(inboundService, timeout(2_000)).startInboundGreeting("speech-call");
        handler.onPlaybackComplete("speech-call");
        verify(inboundService, timeout(2_000)).markInboundListening("speech-call");
        handler.onMedia("speech-call", mulawFrame((byte) 0x00));
        handler.onMedia("speech-call", mulawFrame((byte) 0x00));
        handler.onMedia("speech-call", mulawFrame((byte) 0xff));

        try { Thread.sleep(250); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }

        verify(inboundService, timeout(2_000)).markInboundListening("speech-call");
        verify(inboundService, timeout(2_000).times(1)).processInboundFirstUtterance(
                org.mockito.ArgumentMatchers.eq("speech-call"),
                org.mockito.ArgumentMatchers.argThat(wav -> wav != null && wav.length == 44 + 960
                        && wav[0] == 'R' && wav[1] == 'I' && wav[2] == 'F' && wav[3] == 'F'));
    }

    @Test
    void inboundSilenceDoesNotDispatchSttAndGreetingMediaIsSkipped() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        when(inboundService.inboundVoiceResult("silence-call")).thenReturn(new CommunicationTestDtos.InboundVoiceResult(
                "silence-call", "caller", "did", "INBOUND", "RINGING", "hi-IN", "Sarvam",
                null, null, null, null, null, null, "CONNECTED", null, null, null, null, null, null,
                "RESOLVED", null));
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getVad().setSilenceTimeoutMs(25);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(inboundService, properties);

        handler.onIncomingCall("silence-call", "caller", "did");
        handler.onStreamStart("silence-call");
        handler.onMedia("silence-call", mulawFrame((byte) 0x00));
        verify(inboundService, org.mockito.Mockito.never()).processInboundFirstUtterance(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        handler.onPlaybackComplete("silence-call");
        handler.onMedia("silence-call", mulawFrame((byte) 0xff));

        verify(inboundService, org.mockito.Mockito.never()).processInboundFirstUtterance(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void streamStopFlushesMinimumDurationActiveInboundSpeechBeforeCleanup() {
        CommunicationTestService inboundService = mock(CommunicationTestService.class);
        when(inboundService.inboundVoiceResult("stop-call")).thenReturn(new CommunicationTestDtos.InboundVoiceResult(
                "stop-call", "caller", "did", "INBOUND", "RINGING", "hi-IN", "Sarvam",
                null, null, null, null, null, null, "CONNECTED", null, null, null, null, null, null,
                "RESOLVED", null));
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getVad().setMinSpeechMs(20);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(inboundService, properties);

        handler.onIncomingCall("stop-call", "caller", "did");
        handler.onStreamStart("stop-call");
        verify(inboundService, timeout(2_000)).startInboundGreeting("stop-call");
        handler.onPlaybackComplete("stop-call");
        verify(inboundService, timeout(2_000)).markInboundListening("stop-call");
        handler.onMedia("stop-call", mulawFrame((byte) 0x00));
        handler.onMedia("stop-call", mulawFrame((byte) 0x00));
        handler.onStreamStop("stop-call", "normal");

        verify(inboundService, timeout(2_000).times(1)).processInboundFirstUtterance(
                org.mockito.ArgumentMatchers.eq("stop-call"), org.mockito.ArgumentMatchers.any());
        verify(inboundService, timeout(2_000)).onInboundStreamStopped("stop-call", "normal");
    }

    private byte[] mulawFrame(byte value) {
        byte[] frame = new byte[160];
        java.util.Arrays.fill(frame, value);
        return frame;
    }

    @Test
    void inboundEndpointPreservesResolvedRoutingMetadataWhenAddingWebsocketStatus() {
        UUID channelId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(service.latestInboundVoiceResult()).thenReturn(new CommunicationTestDtos.InboundVoiceResult(
                "call-1", "caller", "00919999000001", "INBOUND", "ENDED", "hi-IN", "Sarvam", "hello",
                null, null, null, null, "completed", "DISCONNECTED", "+919999000001", channelId, tenantId,
                null, null, "DOTVOICE", "RESOLVED", null));
        when(service.inboundVoiceWebsocketStatus()).thenReturn("CONNECTED");

        CommunicationTestDtos.InboundVoiceResult result = controller.inboundVoice();

        assertEquals("CONNECTED", result.websocketStatus());
        assertEquals("RESOLVED", result.routingStatus());
        assertEquals("+919999000001", result.normalizedDid());
        assertEquals(channelId, result.communicationChannelId());
        assertEquals(tenantId, result.tenantId());
    }

    @Test
    void inboundVoiceJsonExposesAivaV2RuntimeAfterAivaTurn() throws Exception {
        CommunicationTestDtos.InboundVoiceResult result = new CommunicationTestDtos.InboundVoiceResult(
                "call-a", "caller", "did", "INBOUND", "CAPTURED", "hi-IN", "Sarvam", null,
                null, null, null, null, null, "CONNECTED", null, null, null, null, null, null, null, null)
                .withAivaTurn("voice-call-a", 1, "FIND_DOCTOR", "FIND_DOCTOR", "response");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertEquals("AIVA_V2", json.get("aivaRuntime").asText());
    }
}
