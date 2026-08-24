package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderResultEntryCommand(
        UUID labOrderSampleId,
        List<LabOrderResultItemCommand> items,
        String comments,
        List<UUID> orderedTestIds
) {
    public LabOrderResultEntryCommand(List<LabOrderResultItemCommand> items, String comments) {
        this(null, items, comments, List.of());
    }

    public LabOrderResultEntryCommand(List<LabOrderResultItemCommand> items, String comments, List<UUID> orderedTestIds) {
        this(null, items, comments, orderedTestIds == null ? List.of() : orderedTestIds);
    }
}
