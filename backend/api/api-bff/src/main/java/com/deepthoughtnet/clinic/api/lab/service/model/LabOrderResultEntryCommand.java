package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;
import java.util.UUID;

public record LabOrderResultEntryCommand(
        List<LabOrderResultItemCommand> items,
        String comments,
        List<UUID> orderedTestIds
) {
    public LabOrderResultEntryCommand(List<LabOrderResultItemCommand> items, String comments) {
        this(items, comments, List.of());
    }
}
