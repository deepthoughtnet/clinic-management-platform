package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;

public record LabOrderPublishReportRequest(
        List<String> deliveryChannels,
        String publishNotes
) {
}
