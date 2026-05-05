package com.deepthoughtnet.clinic.platform.audit;

import java.util.List;
import java.util.UUID;

public interface AuditEventQueryService {
    List<AuditEventRecord> listForEntity(UUID tenantId, String entityType, UUID entityId);
}
