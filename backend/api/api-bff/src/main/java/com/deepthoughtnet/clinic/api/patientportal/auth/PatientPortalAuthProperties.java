package com.deepthoughtnet.clinic.api.patientportal.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinic.patient-portal.auth")
public class PatientPortalAuthProperties {
    public enum Mode {
        DEV_OTP,
        ACCESS_APPROVAL,
        OTP
    }

    private Duration otpTtl = Duration.ofMinutes(5);
    private Duration resendCooldown = Duration.ofSeconds(60);
    private int maxAttempts = 5;
    private boolean exposeDevOtp = false;
    private Mode mode = Mode.OTP;
    private Duration accessCodeTtl = Duration.ofDays(7);
    private String sessionSecret = "change-me-patient-portal-session-secret";
    private Duration sessionTtl = Duration.ofHours(12);

    public Duration getOtpTtl() {
        return otpTtl;
    }

    public void setOtpTtl(Duration otpTtl) {
        this.otpTtl = otpTtl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isExposeDevOtp() {
        return exposeDevOtp;
    }

    public void setExposeDevOtp(boolean exposeDevOtp) {
        this.exposeDevOtp = exposeDevOtp;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OTP : mode;
    }

    public Duration getAccessCodeTtl() {
        return accessCodeTtl;
    }

    public void setAccessCodeTtl(Duration accessCodeTtl) {
        this.accessCodeTtl = accessCodeTtl;
    }

    public String getSessionSecret() {
        return sessionSecret;
    }

    public void setSessionSecret(String sessionSecret) {
        this.sessionSecret = sessionSecret;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }
}
