package com.deepthoughtnet.clinic.platform.audit;

import java.util.UUID;

public interface AuditEventPublisher {
    UUID record(AuditEventCommand command);
}
