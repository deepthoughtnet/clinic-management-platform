package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderResultRequest(
        String labOrderSampleId,
        @NotEmpty List<LabOrderResultItemRequest> items,
        @Size(max = 250) String comments,
        List<UUID> orderedTestIds
) {
    public record LabOrderResultItemRequest(
            @NotNull UUID labOrderItemId,
            @Size(max = 120) String resultValue,
            @Size(max = 30) String unit,
            @Size(max = 120) String referenceRange,
            List<LabOrderResultComponentRequest> componentResults
    ) {
    }

    public record LabOrderResultComponentRequest(
            @Size(max = 60) String parameterName,
            @Size(max = 60) String componentName,
            @Size(max = 120) String resultValue,
            @Size(max = 30) String unit,
            @Size(max = 120) String referenceRange
    ) {
    }
}
