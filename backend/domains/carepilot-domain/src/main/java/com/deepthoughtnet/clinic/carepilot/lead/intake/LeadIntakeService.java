package com.deepthoughtnet.clinic.carepilot.lead.intake;

import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Entry-point abstraction for future multi-channel lead ingestion. */
@Service
public class LeadIntakeService {
    private final LeadService leadService;

    public LeadIntakeService(LeadService leadService) {
        this.leadService = leadService;
    }

    public LeadRecord intake(UUID tenantId, LeadUpsertCommand command, UUID actorId) {
        return leadService.create(tenantId, command, actorId);
    }
}
