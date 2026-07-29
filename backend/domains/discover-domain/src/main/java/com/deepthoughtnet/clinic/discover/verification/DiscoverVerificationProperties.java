package com.deepthoughtnet.clinic.discover.verification;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinic.discover.verification")
public class DiscoverVerificationProperties {
    private Duration challengeTtl = Duration.ofMinutes(5);
    private Duration resendCooldown = Duration.ofSeconds(60);
    private int maxAttempts = 5;
    private Duration sessionTtl = Duration.ofHours(12);
    private boolean exposeDevelopmentCode = false;
    private UUID deliveryTenantId = UUID.nameUUIDFromBytes("discover-verification".getBytes());

    public Duration getChallengeTtl() {
        return challengeTtl;
    }

    public void setChallengeTtl(Duration challengeTtl) {
        this.challengeTtl = challengeTtl;
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

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public boolean isExposeDevelopmentCode() {
        return exposeDevelopmentCode;
    }

    public void setExposeDevelopmentCode(boolean exposeDevelopmentCode) {
        this.exposeDevelopmentCode = exposeDevelopmentCode;
    }

    public UUID getDeliveryTenantId() {
        return deliveryTenantId;
    }

    public void setDeliveryTenantId(UUID deliveryTenantId) {
        this.deliveryTenantId = deliveryTenantId;
    }
}
