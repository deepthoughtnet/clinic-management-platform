package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderDirectCreateCommand(
        UUID patientId,
        UUID doctorUserId,
        List<UUID> testIds,
        String notes
) {
}
