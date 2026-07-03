package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record LabOrderSamplesCollectRequest(
        @NotEmpty List<@Valid LabSampleCollectionRequest> samples
) {
}
