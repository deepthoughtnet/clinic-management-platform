package com.deepthoughtnet.clinic.api.carepilot;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tracks last reminder scheduler scans for tenant-scoped runtime visibility.
 */
@Service
public class CarePilotRuntimeSchedulerMonitor {
    private final Map<UUID, OffsetDateTime> lastReminderScanByTenant = new ConcurrentHashMap<>();
    private volatile OffsetDateTime lastGlobalReminderScanAt;
    private final boolean reminderSchedulerEnabled;

    public CarePilotRuntimeSchedulerMonitor(@Value("${carepilot.reminders.enabled:false}") boolean reminderSchedulerEnabled) {
        this.reminderSchedulerEnabled = reminderSchedulerEnabled;
    }

    /** Records that reminder scan ran for the given tenant. */
    public void markReminderScan(UUID tenantId, OffsetDateTime scannedAt) {
        if (tenantId == null || scannedAt == null) {
            return;
        }
        lastReminderScanByTenant.put(tenantId, scannedAt);
        markGlobalReminderScan(scannedAt);
    }

    /** Records the latest reminder scheduler heartbeat across all tenants. */
    public void markGlobalReminderScan(OffsetDateTime scannedAt) {
        if (scannedAt == null) {
            return;
        }
        lastGlobalReminderScanAt = scannedAt;
    }

    /** Returns the most recent reminder scheduler scan timestamp for one tenant. */
    public OffsetDateTime lastReminderScanAt(UUID tenantId) {
        return tenantId == null ? null : lastReminderScanByTenant.get(tenantId);
    }

    /** Returns the latest reminder scheduler scan timestamp across all tenants. */
    public OffsetDateTime lastGlobalReminderScanAt() {
        return lastGlobalReminderScanAt;
    }

    /** Returns scheduler status string for dashboard consumption. */
    public String reminderSchedulerStatus() {
        return reminderSchedulerEnabled ? "ENABLED" : "DISABLED";
    }
}
