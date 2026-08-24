package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;

public record LabOrderPublishReportRequest(
        List<String> deliveryChannels,
        List<UUID> orderedTestIds,
        String publishNotes,
        String reportMode
) {
}
