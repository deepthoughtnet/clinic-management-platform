package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LabOrderResultRequest(
        @NotEmpty List<LabOrderResultItemRequest> items,
        @Size(max = 4000) String comments
) {
    public record LabOrderResultItemRequest(
            UUID labOrderItemId,
            @Size(max = 256) String resultValue,
            @Size(max = 64) String unit,
            @Size(max = 256) String referenceRange,
            List<LabOrderResultComponentRequest> componentResults
    ) {
    }

    public record LabOrderResultComponentRequest(
            @Size(max = 256) String componentName,
            @Size(max = 256) String resultValue,
            @Size(max = 64) String unit,
            @Size(max = 256) String referenceRange
    ) {
    }
}
