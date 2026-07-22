package com.deepthoughtnet.clinic.platform.modulith.events;

public interface ModuleBusinessEventListener<E extends ModuleBusinessEvent> {
    String listenerName();

    String listenerModule();

    String eventType();

    Class<E> eventClass();

    void handle(E event);
}
