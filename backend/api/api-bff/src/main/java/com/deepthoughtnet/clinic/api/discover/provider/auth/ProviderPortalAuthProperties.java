package com.deepthoughtnet.clinic.api.discover.provider.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinic.provider-portal.auth")
public class ProviderPortalAuthProperties {
    public enum Mode {
        DEV_OTP,
        ACCESS_APPROVAL,
        OTP
    }

    private Mode mode = Mode.OTP;
    private boolean exposeDevOtp = false;
    private Duration accessCodeTtl = Duration.ofDays(7);
    private Duration sessionTtl = Duration.ofHours(12);

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OTP : mode;
    }

    public boolean isExposeDevOtp() {
        return exposeDevOtp;
    }

    public void setExposeDevOtp(boolean exposeDevOtp) {
        this.exposeDevOtp = exposeDevOtp;
    }

    public Duration getAccessCodeTtl() {
        return accessCodeTtl;
    }

    public void setAccessCodeTtl(Duration accessCodeTtl) {
        this.accessCodeTtl = accessCodeTtl;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }
}
