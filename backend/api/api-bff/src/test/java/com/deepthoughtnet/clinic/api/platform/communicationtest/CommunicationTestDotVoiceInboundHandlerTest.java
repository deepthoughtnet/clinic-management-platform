package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceConversationState;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommunicationTestDotVoiceInboundHandlerTest {
    @Test
    void playbackPreparationFailureReturnsInboundCallToListening() {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(
                service, new VoiceTestProperties());
        String callId = "playback-preparation-failed";
        handler.onIncomingCall(callId, "+10000000000", "+10000000001");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimes = (Map<String, Object>) ReflectionTestUtils.getField(handler, "inboundConversations");
        Object runtime = runtimes.get(callId);
        ReflectionTestUtils.setField(runtime, "state", VoiceConversationState.PROMPTING);
        ((AtomicBoolean) ReflectionTestUtils.getField(runtime, "botSpeaking")).set(true);

        handler.onPlaybackPreparationFailed(callId);

        assertThat(ReflectionTestUtils.getField(runtime, "state")).isEqualTo(VoiceConversationState.LISTENING);
        assertThat(((AtomicBoolean) ReflectionTestUtils.getField(runtime, "botSpeaking")).get()).isFalse();
        verify(service).markInboundListening(callId);
    }
}
