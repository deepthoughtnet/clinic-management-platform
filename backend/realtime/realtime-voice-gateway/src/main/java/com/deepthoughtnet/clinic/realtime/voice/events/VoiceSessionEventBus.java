package com.deepthoughtnet.clinic.realtime.voice.events;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * In-process event bus used by websocket transport foundation.
 */
@Component
public class VoiceSessionEventBus {
    private final ConcurrentHashMap<UUID, Set<Consumer<VoiceRealtimeEvent>>> listeners = new ConcurrentHashMap<>();

    public AutoCloseable subscribe(UUID sessionId, Consumer<VoiceRealtimeEvent> listener) {
        listeners.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(listener);
        return () -> listeners.getOrDefault(sessionId, Set.of()).remove(listener);
    }

    public void publish(VoiceRealtimeEvent event) {
        listeners.getOrDefault(event.sessionId(), Set.of()).forEach(listener -> listener.accept(event));
    }
}
