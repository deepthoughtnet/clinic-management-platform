package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderPublishReportCommand(
        List<String> deliveryChannels,
        List<UUID> orderedTestIds,
        String publishNotes,
        String reportMode
) {
}
