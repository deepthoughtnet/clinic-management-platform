package com.deepthoughtnet.clinic.platform.outbox;

import java.util.UUID;

public class NoopOutboxEventPublisher implements OutboxEventPublisher {
    @Override
    public UUID publish(OutboxEventCommand command) {
        return null;
    }
}
