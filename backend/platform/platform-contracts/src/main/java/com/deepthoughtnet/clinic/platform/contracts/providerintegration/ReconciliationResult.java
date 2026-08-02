package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

public record ReconciliationResult(
        String scope,
        long examined,
        long inserted,
        long updated,
        long unchanged,
        long skipped,
        long conflicted,
        long failed,
        List<ReconciliationFailure> failures,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) implements Serializable {
}
