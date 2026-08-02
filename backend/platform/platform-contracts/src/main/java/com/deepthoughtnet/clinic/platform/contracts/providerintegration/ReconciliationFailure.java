package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;

public record ReconciliationFailure(
        String sourceReference,
        String targetReference,
        String code,
        String message
) implements Serializable {
}
