package com.deepthoughtnet.clinic.api.patientportal.careai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiva.response-composer")
public class AivaResponseComposerProperties {
    private boolean enabled = false;
    private int timeoutSeconds = 5;
    private boolean voiceOnly = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    public void setVoiceOnly(boolean voiceOnly) {
        this.voiceOnly = voiceOnly;
    }
}
