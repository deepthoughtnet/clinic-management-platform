package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderVerificationCommand(
        String decision,
        List<UUID> orderedTestIds,
        boolean recollectionRequired,
        String reason,
        String comments
) {
}
