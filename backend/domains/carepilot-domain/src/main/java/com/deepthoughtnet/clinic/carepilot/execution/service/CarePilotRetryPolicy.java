package com.deepthoughtnet.clinic.carepilot.execution.service;

import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized retry policy for CarePilot execution dispatching.
 *
 * <p>This policy is intentionally configuration-driven so operations teams can tune reliability
 * thresholds without changing business flow code.</p>
 */
@Component
public class CarePilotRetryPolicy {
    private final int maxRetries;
    private final long initialBackoffSeconds;
    private final long maxBackoffSeconds;
    private final Set<MessageDeliveryStatus> retryableStatuses;

    public CarePilotRetryPolicy(
            @Value("${carepilot.retry.max-retries:3}") int maxRetries,
            @Value("${carepilot.retry.initial-backoff-seconds:60}") long initialBackoffSeconds,
            @Value("${carepilot.retry.max-backoff-seconds:900}") long maxBackoffSeconds,
            @Value("${carepilot.retry.retryable-statuses:FAILED,PROVIDER_NOT_AVAILABLE,NOT_CONFIGURED}") String retryableStatuses
    ) {
        this.maxRetries = Math.max(1, maxRetries);
        this.initialBackoffSeconds = Math.max(1, initialBackoffSeconds);
        this.maxBackoffSeconds = Math.max(this.initialBackoffSeconds, maxBackoffSeconds);
        this.retryableStatuses = parseRetryableStatuses(retryableStatuses);
    }

    public int maxRetries() {
        return maxRetries;
    }

    /** Returns whether the delivery result should be retried by scheduler flow. */
    public boolean isRetryable(MessageDeliveryStatus status) {
        return status != null && retryableStatuses.contains(status);
    }

    /** Computes exponential backoff for the next attempt with an upper cap. */
    public OffsetDateTime computeNextRetryAt(int currentAttemptCount) {
        int exponent = Math.max(0, currentAttemptCount - 1);
        long backoff = (long) Math.min(maxBackoffSeconds, initialBackoffSeconds * Math.pow(2, exponent));
        return OffsetDateTime.now().plusSeconds(backoff);
    }

    private Set<MessageDeliveryStatus> parseRetryableStatuses(String configuredStatuses) {
        Set<MessageDeliveryStatus> statuses = EnumSet.noneOf(MessageDeliveryStatus.class);
        for (String token : configuredStatuses.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isEmpty()) {
                statuses.add(MessageDeliveryStatus.valueOf(normalized));
            }
        }
        if (statuses.isEmpty()) {
            statuses.add(MessageDeliveryStatus.FAILED);
        }
        return statuses;
    }
}
