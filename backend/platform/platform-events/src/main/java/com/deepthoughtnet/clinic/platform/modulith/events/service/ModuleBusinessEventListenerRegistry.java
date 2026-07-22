package com.deepthoughtnet.clinic.platform.modulith.events.service;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ModuleBusinessEventListenerRegistry {
    private final Map<String, ModuleBusinessEventListener<? extends ModuleBusinessEvent>> listenersByName;
    private final Map<String, List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>>> listenersByEventType;

    public ModuleBusinessEventListenerRegistry(List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>> listeners) {
        Map<String, ModuleBusinessEventListener<? extends ModuleBusinessEvent>> byName = new LinkedHashMap<>();
        Map<String, List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>>> byType = new LinkedHashMap<>();
        for (ModuleBusinessEventListener<? extends ModuleBusinessEvent> listener : listeners) {
            if (listener == null) {
                continue;
            }
            byName.put(listener.listenerName(), listener);
            byType.computeIfAbsent(listener.eventType(), key -> new java.util.ArrayList<>()).add(listener);
        }
        this.listenersByName = Map.copyOf(byName);
        Map<String, List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>>> copied = new LinkedHashMap<>();
        byType.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        this.listenersByEventType = Map.copyOf(copied);
    }

    public Collection<ModuleBusinessEventListener<? extends ModuleBusinessEvent>> all() {
        return listenersByName.values();
    }

    public List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>> listenersFor(String eventType) {
        return listenersByEventType.getOrDefault(eventType, List.of());
    }

    public ModuleBusinessEventListener<? extends ModuleBusinessEvent> findByName(String listenerName) {
        return listenersByName.get(listenerName);
    }
}
