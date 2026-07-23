package com.deepthoughtnet.clinic.billing.service;

import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import java.util.UUID;

/**
 * Read-only bill snapshot used by notification workflows to validate payment reminder freshness.
 */
public interface PaymentReminderStateReader {

    /**
     * Returns the current reminder-relevant bill state for the given tenant and bill identifier.
     *
     * @param tenantId tenant-scoped bill owner
     * @param billId bill identifier
     * @return current reminder state, or a missing-state marker when the bill does not exist
     */
    PaymentReminderState findCurrentState(UUID tenantId, UUID billId);
}
