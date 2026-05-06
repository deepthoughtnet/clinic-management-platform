package com.deepthoughtnet.clinic.api.reliability.service;

import com.deepthoughtnet.clinic.api.reliability.db.AuditLogEntity;
import com.deepthoughtnet.clinic.api.reliability.db.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public UUID log(UUID tenantId, String entityType, UUID entityId, String action, UUID performedBy, String payloadJson) {
        AuditLogEntity entity = AuditLogEntity.create(tenantId, entityType, entityId, action, performedBy, payloadJson);
        return auditLogRepository.save(entity).getId();
    }
}
