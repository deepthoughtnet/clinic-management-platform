package com.deepthoughtnet.clinic.api.careai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "careai.tasks.sla")
public class CareAiTaskSlaProperties {
    private boolean enabled = true;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private Duration dueSoonWindow = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public Duration getDueSoonWindow() {
        return dueSoonWindow;
    }

    public void setDueSoonWindow(Duration dueSoonWindow) {
        this.dueSoonWindow = dueSoonWindow;
    }
}
