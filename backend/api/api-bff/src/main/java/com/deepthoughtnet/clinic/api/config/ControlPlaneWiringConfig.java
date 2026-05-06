package com.deepthoughtnet.clinic.api.config;

import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisioner;
import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisionerImpl;
import com.deepthoughtnet.clinic.identity.service.keycloak.NoopKeycloakAdminProvisioner;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ControlPlaneWiringConfig {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneWiringConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "clinic.keycloak.admin", name = "enabled", havingValue = "true")
    public Keycloak keycloakAdmin(
            @Value("${clinic.keycloak.admin.serverUrl:}") String serverUrl,
            @Value("${clinic.keycloak.admin.adminRealm:master}") String adminRealm,
            @Value("${clinic.keycloak.admin.targetRealm:}") String targetRealm,
            @Value("${clinic.keycloak.admin.clientId:}") String clientId,
            @Value("${clinic.keycloak.admin.clientSecret:}") String clientSecret,
            @Value("${clinic.keycloak.admin.username:}") String username,
            @Value("${clinic.keycloak.admin.password:}") String password
    ) {
        require("clinic.keycloak.admin.serverUrl", serverUrl);
        require("clinic.keycloak.admin.adminRealm", adminRealm);
        require("clinic.keycloak.admin.clientId", clientId);

        boolean useClientCredentials = clientSecret != null && !clientSecret.isBlank();

        if (!useClientCredentials) {
            require("clinic.keycloak.admin.username", username);
            require("clinic.keycloak.admin.password", password);
        }

        try {
            KeycloakBuilder builder = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm(adminRealm)
                    .clientId(clientId);

            if (useClientCredentials) {
                builder.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                        .clientSecret(clientSecret);
                log.info("Keycloak Admin enabled (client_credentials). adminRealm={}, targetRealm={}", adminRealm,
                        (targetRealm == null || targetRealm.isBlank()) ? "(not set)" : targetRealm);
            } else {
                builder.grantType(OAuth2Constants.PASSWORD)
                        .username(username)
                        .password(password);
                log.info("Keycloak Admin enabled (password grant). adminRealm={}, targetRealm={}, username={}", adminRealm,
                        (targetRealm == null || targetRealm.isBlank()) ? "(not set)" : targetRealm,
                        username);
            }

            Keycloak keycloak = builder.build();
            keycloak.realm(adminRealm).toRepresentation();
            return keycloak;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Keycloak Admin client", e);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "clinic.keycloak.admin", name = "enabled", havingValue = "true")
    public KeycloakAdminProvisioner keycloakAdminProvisioner(
            Keycloak keycloakAdmin,
            @Value("${clinic.keycloak.admin.targetRealm:}") String targetRealm,
            @Value("${clinic.keycloak.admin.adminRealm:master}") String adminRealm
    ) {
        String realmToUse = (targetRealm != null && !targetRealm.isBlank()) ? targetRealm : adminRealm;
        return new KeycloakAdminProvisionerImpl(keycloakAdmin, realmToUse);
    }

    @Bean
    @ConditionalOnProperty(prefix = "clinic.keycloak.admin", name = "enabled", havingValue = "false", matchIfMissing = true)
    public KeycloakAdminProvisioner noopKeycloakAdminProvisioner() {
        log.warn("Keycloak Admin provisioning is disabled (clinic.keycloak.admin.enabled=false).");
        return new NoopKeycloakAdminProvisioner();
    }

    private static void require(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
    }
}
