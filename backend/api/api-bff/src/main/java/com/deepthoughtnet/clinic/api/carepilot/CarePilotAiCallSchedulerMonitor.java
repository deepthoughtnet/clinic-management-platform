package com.deepthoughtnet.clinic.api.carepilot;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Tracks AI call scheduler execution health metrics for operational visibility. */
@Service
public class CarePilotAiCallSchedulerMonitor {
    private final boolean enabled;
    private final Duration fixedDelay;

    private volatile OffsetDateTime lastRunAt;
    private final AtomicInteger lastProcessedCount = new AtomicInteger();
    private final AtomicInteger lastDispatchedCount = new AtomicInteger();
    private final AtomicInteger lastFailedCount = new AtomicInteger();
    private final AtomicInteger lastSkippedCount = new AtomicInteger();
    private final AtomicLong lastDurationMs = new AtomicLong();

    public CarePilotAiCallSchedulerMonitor(
            @Value("${carepilot.ai-calls.scheduler.enabled:false}") boolean enabled,
            @Value("${carepilot.ai-calls.scheduler.fixed-delay:PT1M}") Duration fixedDelay
    ) {
        this.enabled = enabled;
        this.fixedDelay = fixedDelay;
    }

    public void markRun(OffsetDateTime runAt, int processed, int dispatched, int failed, int skipped, long durationMs) {
        this.lastRunAt = runAt;
        this.lastProcessedCount.set(processed);
        this.lastDispatchedCount.set(dispatched);
        this.lastFailedCount.set(failed);
        this.lastSkippedCount.set(skipped);
        this.lastDurationMs.set(durationMs);
    }

    public boolean enabled() { return enabled; }
    public OffsetDateTime lastRunAt() { return lastRunAt; }
    public OffsetDateTime nextEstimatedRunAt() { return lastRunAt == null ? null : lastRunAt.plus(fixedDelay); }
    public int lastProcessedCount() { return lastProcessedCount.get(); }
    public int lastDispatchedCount() { return lastDispatchedCount.get(); }
    public int lastFailedCount() { return lastFailedCount.get(); }
    public int lastSkippedCount() { return lastSkippedCount.get(); }
    public long lastDurationMs() { return lastDurationMs.get(); }
}
