package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.LocalDateTime;

public record BookingSlotSummary(
        String slotId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String displayLabel,
        boolean available
) implements Serializable {
}
