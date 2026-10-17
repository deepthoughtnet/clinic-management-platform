package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DotVoiceInboundListenerLifecycleTest {
    @Test
    void lifecycleStartsAndStopsTheApplicationOwnedListenerWithoutAnHttpCall() {
        DotVoiceMediaStreamClient client = mock(DotVoiceMediaStreamClient.class);
        DotVoiceInboundListenerLifecycle lifecycle = new DotVoiceInboundListenerLifecycle(client);

        lifecycle.start();
        lifecycle.start();
        verify(client, times(1)).startPersistentListener();

        lifecycle.stop();
        lifecycle.stop();
        verify(client, times(1)).stopPersistentListener();
    }
}
