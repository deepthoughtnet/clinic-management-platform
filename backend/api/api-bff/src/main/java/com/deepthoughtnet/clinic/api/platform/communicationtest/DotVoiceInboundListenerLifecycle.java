package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Owns the single application-wide DotVoice inbound listener. */
@Component
public class DotVoiceInboundListenerLifecycle implements SmartLifecycle {
    private final DotVoiceMediaStreamClient client;
    private volatile boolean running;

    public DotVoiceInboundListenerLifecycle(DotVoiceMediaStreamClient client) {
        this.client = client;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        client.startPersistentListener();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        client.stopPersistentListener();
    }

    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MIN_VALUE; }
}
