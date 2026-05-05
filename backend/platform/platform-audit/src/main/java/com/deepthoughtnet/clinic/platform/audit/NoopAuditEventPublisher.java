package com.deepthoughtnet.clinic.platform.audit;

import java.util.UUID;

public final class NoopAuditEventPublisher implements AuditEventPublisher {
    @Override
    public UUID record(AuditEventCommand command) {
        return UUID.randomUUID();
    }
}
