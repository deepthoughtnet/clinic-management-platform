package com.deepthoughtnet.clinic.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ControlPlaneWiringConfigTest {

    @Test
    void keycloakAdminDoesNotConnectDuringBeanCreation() {
        ControlPlaneWiringConfig config = new ControlPlaneWiringConfig();

        assertDoesNotThrow(() -> {
            var keycloak = config.keycloakAdmin(
                    "http://127.0.0.1:1",
                    "master",
                    "clinic-management",
                    "admin-cli",
                    "",
                    "admin",
                    "admin"
            );
            keycloak.close();
        });
    }
}
