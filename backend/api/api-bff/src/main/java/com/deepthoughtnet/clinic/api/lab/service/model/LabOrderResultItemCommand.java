package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderResultItemCommand(
        UUID labOrderItemId,
        String resultValue,
        String unit,
        String referenceRange,
        List<LabOrderResultComponentCommand> componentResults
) {
}
