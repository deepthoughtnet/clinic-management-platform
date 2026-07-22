package com.deepthoughtnet.clinic.platform.modulith.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jeevanam.platform.events")
public class ModuleBusinessEventProperties {
    /**
     * Scheduler delay for polling event listener rows.
     */
    private String dispatchFixedDelay = "PT10S";

    /**
     * Maximum attempts before a listener row is dead-lettered.
     */
    private int maxAttempts = 3;

    /**
     * Base retry backoff used when a listener is retryable.
     */
    private String retryBackoff = "PT1M";

    /**
     * Stale processing rows older than this threshold are reclaimed on restart.
     */
    private String staleProcessingTimeout = "PT5M";

    public String getDispatchFixedDelay() {
        return dispatchFixedDelay;
    }

    public void setDispatchFixedDelay(String dispatchFixedDelay) {
        if (dispatchFixedDelay != null && !dispatchFixedDelay.isBlank()) {
            this.dispatchFixedDelay = dispatchFixedDelay.trim();
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public String getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(String retryBackoff) {
        if (retryBackoff != null && !retryBackoff.isBlank()) {
            this.retryBackoff = retryBackoff.trim();
        }
    }

    public String getStaleProcessingTimeout() {
        return staleProcessingTimeout;
    }

    public void setStaleProcessingTimeout(String staleProcessingTimeout) {
        if (staleProcessingTimeout != null && !staleProcessingTimeout.isBlank()) {
            this.staleProcessingTimeout = staleProcessingTimeout.trim();
        }
    }
}
