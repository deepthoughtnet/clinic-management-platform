package com.deepthoughtnet.clinic.api.discover.provider.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProviderPortalAuthModeStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(ProviderPortalAuthModeStartupLogger.class);

    private final ProviderPortalAuthProperties properties;

    public ProviderPortalAuthModeStartupLogger(ProviderPortalAuthProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logProviderPortalAuthMode() {
        log.info("Provider portal auth mode: {}", properties.getMode());
    }
}
