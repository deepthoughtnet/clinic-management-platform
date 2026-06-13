package com.deepthoughtnet.clinic.api.lab.service.model;

import java.util.List;

public record LabOrderResultEntryCommand(
        List<LabOrderResultItemCommand> items,
        String comments
) {
}
