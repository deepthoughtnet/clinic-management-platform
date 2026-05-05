package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditEntity;

public interface AiRequestAuditService {
    AiRequestAuditEntity record(AiRequestAuditCommand command);
}
