package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;

public record LabOrderPublishReportCommand(
        List<String> deliveryChannels,
        String publishNotes
) {
}
