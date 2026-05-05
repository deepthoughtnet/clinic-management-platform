package com.deepthoughtnet.clinic.notification.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.notifications")
public class NotificationProperties {

    private boolean enabled = true;
    private String channel = "email";
    private Dispatcher dispatcher = new Dispatcher();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Dispatcher getDispatcher() { return dispatcher; }
    public void setDispatcher(Dispatcher dispatcher) { this.dispatcher = dispatcher; }

    public static class Dispatcher {
        private boolean enabled = true;
        private int batchSize = 25;
        private long pollingIntervalMs = 30000;
        private int maxAttempts = 3;
        private long retryBackoffMs = 60000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getPollingIntervalMs() { return pollingIntervalMs; }
        public void setPollingIntervalMs(long pollingIntervalMs) { this.pollingIntervalMs = pollingIntervalMs; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

        public int normalizedBatchSize() {
            return batchSize <= 0 ? 25 : Math.min(batchSize, 100);
        }

        public int normalizedMaxAttempts() {
            return maxAttempts <= 0 ? 3 : Math.min(maxAttempts, 25);
        }

        public Duration normalizedRetryBackoff() {
            return retryBackoffMs <= 0 ? Duration.ofMinutes(1) : Duration.ofMillis(retryBackoffMs);
        }
    }
}
