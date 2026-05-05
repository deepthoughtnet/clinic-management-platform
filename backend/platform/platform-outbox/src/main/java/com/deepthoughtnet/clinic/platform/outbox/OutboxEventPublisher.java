package com.deepthoughtnet.clinic.platform.outbox;

import java.util.UUID;

public interface OutboxEventPublisher {
    UUID publish(OutboxEventCommand command);
}
