package com.deepthoughtnet.clinic.platform.audit;

import java.util.UUID;

public interface AuditEventHandler {
    void afterAuditRecorded(UUID auditEventId, AuditEventCommand command);
}
