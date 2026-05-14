package com.deepthoughtnet.clinic.api.ops;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** In-memory visibility for distributed scheduler lock outcomes. */
@Service
public class SchedulerLockMonitor {
    public record LockState(String schedulerName, OffsetDateTime lastAcquiredAt, OffsetDateTime lastSkippedAt, long acquireCount, long skipCount) {}

    private final Map<String, MutableState> states = new ConcurrentHashMap<>();

    public void markAcquired(String schedulerName) {
        MutableState state = states.computeIfAbsent(schedulerName, key -> new MutableState());
        state.lastAcquiredAt = OffsetDateTime.now();
        state.acquireCount++;
    }

    public void markSkipped(String schedulerName) {
        MutableState state = states.computeIfAbsent(schedulerName, key -> new MutableState());
        state.lastSkippedAt = OffsetDateTime.now();
        state.skipCount++;
    }

    public Map<String, LockState> snapshot() {
        return states.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> new LockState(e.getKey(), e.getValue().lastAcquiredAt, e.getValue().lastSkippedAt, e.getValue().acquireCount, e.getValue().skipCount)
        ));
    }

    private static final class MutableState {
        private OffsetDateTime lastAcquiredAt;
        private OffsetDateTime lastSkippedAt;
        private long acquireCount;
        private long skipCount;
    }
}
