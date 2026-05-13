package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignDeliveryAttemptRecord;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import java.util.List;

/**
 * Read-only timeline view for an execution, including delivery attempts and status events.
 */
public record CarePilotExecutionTimelineRecord(
        CampaignExecutionRecord execution,
        List<CampaignDeliveryAttemptRecord> deliveryAttempts,
        List<CampaignDeliveryEventRecord> deliveryEvents,
        List<ExecutionTimelineEventRecord> statusEvents
) {}
