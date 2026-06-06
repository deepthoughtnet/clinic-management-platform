package com.deepthoughtnet.clinic.messaging.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls CarePilot email messaging provider activation.
 */
@ConfigurationProperties(prefix = "clinic.carepilot.messaging.email")
public class CarePilotEmailMessagingProperties {
    /**
     * Explicit enablement flag for CarePilot email channel dispatch.
     */
    private boolean enabled = false;
    /**
     * Logical provider key (for example: disabled, smtp, mock).
     */
    private String provider = "disabled";
    /**
     * Optional from-address override for CarePilot reminders.
     */
    private String fromAddress;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
}
