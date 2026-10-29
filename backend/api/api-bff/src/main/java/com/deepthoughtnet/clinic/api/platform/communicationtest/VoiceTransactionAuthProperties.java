package com.deepthoughtnet.clinic.api.platform.communicationtest;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application defaults and trusted-ID keyed overrides for Voice transaction authentication. */
@ConfigurationProperties(prefix = "clinic.aiva.voice.transaction-auth")
public class VoiceTransactionAuthProperties {
    private final PolicyOverride defaultPolicy = new PolicyOverride();
    private Map<String, PolicyOverride> tenantOverrides = new LinkedHashMap<>();
    private Map<String, PolicyOverride> clinicOverrides = new LinkedHashMap<>();

    public PolicyOverride getDefaultPolicy() { return defaultPolicy; }
    public Map<String, PolicyOverride> getTenantOverrides() { return tenantOverrides; }
    public void setTenantOverrides(Map<String, PolicyOverride> values) {
        tenantOverrides = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
    public Map<String, PolicyOverride> getClinicOverrides() { return clinicOverrides; }
    public void setClinicOverrides(Map<String, PolicyOverride> values) {
        clinicOverrides = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public static class PolicyOverride {
        private Boolean enabled;
        private VoiceTransactionAuthPolicy.AuthenticationMode authenticationMode;
        private VoiceTransactionAuthPolicy.TrustMode trustMode;
        private Integer authTtlMinutes;
        private Boolean bookingAuthRequired;
        private Boolean lookupAuthRequired;
        private Boolean cancellationAuthRequired;
        private Boolean rescheduleAuthRequired;
        private Boolean guestBookingEnabled;
        private Boolean minimalRegistrationEnabled;
        private Boolean guestBookingRequiresMobileConfirmation;
        private Integer guestBookingCapabilityTtlMinutes;
        private Boolean bookingEnabled;
        private Boolean otpRequired;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean value) { enabled = value; }
        public VoiceTransactionAuthPolicy.AuthenticationMode getAuthenticationMode() { return authenticationMode; }
        public void setAuthenticationMode(VoiceTransactionAuthPolicy.AuthenticationMode value) { authenticationMode = value; }
        public VoiceTransactionAuthPolicy.TrustMode getTrustMode() { return trustMode; }
        public void setTrustMode(VoiceTransactionAuthPolicy.TrustMode value) { trustMode = value; }
        public Integer getAuthTtlMinutes() { return authTtlMinutes; }
        public void setAuthTtlMinutes(Integer value) { authTtlMinutes = value; }
        public Boolean getBookingAuthRequired() { return bookingAuthRequired; }
        public void setBookingAuthRequired(Boolean value) { bookingAuthRequired = value; }
        public Boolean getLookupAuthRequired() { return lookupAuthRequired; }
        public void setLookupAuthRequired(Boolean value) { lookupAuthRequired = value; }
        public Boolean getCancellationAuthRequired() { return cancellationAuthRequired; }
        public void setCancellationAuthRequired(Boolean value) { cancellationAuthRequired = value; }
        public Boolean getRescheduleAuthRequired() { return rescheduleAuthRequired; }
        public void setRescheduleAuthRequired(Boolean value) { rescheduleAuthRequired = value; }
        public Boolean getGuestBookingEnabled() { return guestBookingEnabled; }
        public void setGuestBookingEnabled(Boolean value) { guestBookingEnabled = value; }
        public Boolean getMinimalRegistrationEnabled() { return minimalRegistrationEnabled; }
        public void setMinimalRegistrationEnabled(Boolean value) { minimalRegistrationEnabled = value; }
        public Boolean getGuestBookingRequiresMobileConfirmation() { return guestBookingRequiresMobileConfirmation; }
        public void setGuestBookingRequiresMobileConfirmation(Boolean value) { guestBookingRequiresMobileConfirmation = value; }
        public Integer getGuestBookingCapabilityTtlMinutes() { return guestBookingCapabilityTtlMinutes; }
        public void setGuestBookingCapabilityTtlMinutes(Integer value) { guestBookingCapabilityTtlMinutes = value; }
        public Boolean getBookingEnabled() { return bookingEnabled; }
        public void setBookingEnabled(Boolean value) { bookingEnabled = value; }
        public Boolean getOtpRequired() { return otpRequired; }
        public void setOtpRequired(Boolean value) { otpRequired = value; }
    }
}
