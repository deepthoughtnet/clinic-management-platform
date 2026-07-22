package com.deepthoughtnet.clinic.platform.modulith.events;

import java.util.UUID;

public interface ModuleBusinessEventPublisher {
    UUID publish(ModuleBusinessEvent event);
}
