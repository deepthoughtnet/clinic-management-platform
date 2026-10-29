package com.deepthoughtnet.clinic.api.platform.communicationtest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Preferred, compact application-level Voice transaction settings. */
@ConfigurationProperties(prefix = "voice")
public class VoiceOperatorProperties {
    private Boolean bookingEnabled;
    private Boolean otpRequired;
    private Boolean guestBookingEnabled;

    public Boolean getBookingEnabled() { return bookingEnabled; }
    public void setBookingEnabled(Boolean value) { bookingEnabled = value; }
    public Boolean getOtpRequired() { return otpRequired; }
    public void setOtpRequired(Boolean value) { otpRequired = value; }
    public Boolean getGuestBookingEnabled() { return guestBookingEnabled; }
    public void setGuestBookingEnabled(Boolean value) { guestBookingEnabled = value; }
}
