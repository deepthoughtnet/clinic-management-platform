package com.deepthoughtnet.clinic.platform.spring.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cluster-safe lock abstraction for scheduler and operational single-run critical sections.
 */
public interface DistributedLockService {
    /**
     * Attempts to run the callback only when lock is acquired within the wait window.
     *
     * @return true when callback executed under lock; false when lock could not be acquired.
     */
    boolean executeWithLock(String lockKey, Duration waitTimeout, Supplier<Void> callback);
}
