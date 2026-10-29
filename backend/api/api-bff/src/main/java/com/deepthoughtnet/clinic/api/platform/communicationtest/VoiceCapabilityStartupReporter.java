package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Emits non-sensitive Voice capability state once the API has completed startup. */
@Component
public class VoiceCapabilityStartupReporter {
    private static final Logger log = LoggerFactory.getLogger(VoiceCapabilityStartupReporter.class);

    private final boolean sharedEnabled;
    private final boolean alternateVoiceEnabled;
    private final boolean dotVoiceBookingGlobalEnabled;
    private final boolean guestBookingGlobalEnabled;

    public VoiceCapabilityStartupReporter(
            @Value("${aiva.v2.enabled:false}") boolean sharedEnabled,
            VoiceTestProperties voiceProperties,
            VoiceOperatorProperties operatorProperties) {
        this.sharedEnabled = sharedEnabled;
        this.alternateVoiceEnabled = voiceProperties != null && voiceProperties.isAivaV2Enabled();
        this.dotVoiceBookingGlobalEnabled = operatorProperties != null
                && Boolean.TRUE.equals(operatorProperties.getBookingEnabled());
        this.guestBookingGlobalEnabled = operatorProperties != null
                && Boolean.TRUE.equals(operatorProperties.getGuestBookingEnabled());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportCapabilities() {
        log.info("AIVA_V2_CAPABILITIES sharedEnabled={} alternateVoiceEnabled={} dotVoiceBookingGlobalEnabled={} "
                        + "guestBookingGlobalEnabled={} tenantOverrideSupported=true clinicOverrideSupported=true",
                sharedEnabled, alternateVoiceEnabled, dotVoiceBookingGlobalEnabled, guestBookingGlobalEnabled);
    }
}
