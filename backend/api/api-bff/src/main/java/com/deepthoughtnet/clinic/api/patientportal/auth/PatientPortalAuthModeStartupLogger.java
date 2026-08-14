package com.deepthoughtnet.clinic.api.patientportal.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PatientPortalAuthModeStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalAuthModeStartupLogger.class);

    private final PatientPortalAuthProperties properties;

    public PatientPortalAuthModeStartupLogger(PatientPortalAuthProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logPatientPortalAuthMode() {
        log.info("Patient portal auth mode: {}", properties.getMode());
    }
}
