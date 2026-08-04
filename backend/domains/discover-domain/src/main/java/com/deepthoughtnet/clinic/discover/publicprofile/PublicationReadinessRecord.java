package com.deepthoughtnet.clinic.discover.publicprofile;

import java.time.OffsetDateTime;
import java.util.List;

public record PublicationReadinessRecord(
        boolean ready,
        int completenessPercentage,
        List<String> missingFields,
        List<String> invalidFields,
        List<String> warnings,
        String currentStatus,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt
) {
}
