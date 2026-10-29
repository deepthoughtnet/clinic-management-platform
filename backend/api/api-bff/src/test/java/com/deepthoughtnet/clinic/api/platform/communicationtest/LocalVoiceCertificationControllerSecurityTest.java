package com.deepthoughtnet.clinic.api.platform.communicationtest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalVoiceCertificationControllerSecurityTest {
    @Test
    void controllerRequiresExistingPlatformAdminPermission() {
        assertEquals("@permissionChecker.hasRole('PLATFORM_ADMIN')",
                LocalVoiceCertificationController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void callersCannotSupplyTenantPatientOrExecutionAuthorization() {
        var components = Arrays.stream(LocalVoiceCertificationController.StartCallRequest.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(java.util.List.of("callerNumber", "didNumber"), components);
        assertFalse(Arrays.stream(LocalVoiceCertificationController.TranscriptRequest.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("tenant")
                        || component.getName().toLowerCase().contains("patient")
                        || component.getName().toLowerCase().contains("authorization")));
    }
}
