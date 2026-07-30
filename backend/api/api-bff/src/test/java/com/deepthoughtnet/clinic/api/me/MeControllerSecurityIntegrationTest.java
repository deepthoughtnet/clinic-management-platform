package com.deepthoughtnet.clinic.api.me;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.config.RequestContextConfig;
import com.deepthoughtnet.clinic.api.config.SecurityConfig;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthConfig;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalAuthConfig;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.ActiveTenantMembershipService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.ActiveTenantMembershipRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = MeControllerSecurityIntegrationTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8182/auth/realms/clinic-management",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureMockMvc
class MeControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActiveTenantMembershipService activeTenantMembershipService;

    @MockBean
    private PlatformTenantManagementService platformTenantManagementService;

    @MockBean
    private TenantModuleService tenantModuleService;

    @MockBean
    private PermissionChecker permissionChecker;

    @MockBean
    private AuthContextExtractor authContextExtractor;

    @MockBean
    private AppUserProvisioner appUserProvisioner;

    @MockBean
    private TenantRoleResolver tenantRoleResolver;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private PatientPortalSessionTokenService patientPortalSessionTokenService;

    @MockBean
    private DiscoverVerificationService discoverVerificationService;

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReturnsMeAndBootstrapsTenantContext() throws Exception {
        UUID tenantUuid = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();

        when(authContextExtractor.keycloakSub()).thenReturn("sub-123");
        when(authContextExtractor.email()).thenReturn("platform.admin@clinic.local");
        when(authContextExtractor.displayName()).thenReturn("Platform Admin");
        when(authContextExtractor.rolesUpper()).thenReturn(Set.of("PLATFORM_ADMIN"));
        when(authContextExtractor.resolveTenantId("clinic-a")).thenReturn(new TenantId(tenantUuid));
        when(appUserProvisioner.upsertAndReturnId(eq(tenantUuid), eq("sub-123"), eq("platform.admin@clinic.local"), eq("Platform Admin")))
                .thenReturn(appUserId);
        when(tenantRoleResolver.resolveTenantRole(eq(tenantUuid), eq(appUserId), anySet())).thenReturn("PLATFORM_ADMIN");
        when(activeTenantMembershipService.listActiveMemberships("sub-123", "platform.admin@clinic.local"))
                .thenReturn(List.of(new ActiveTenantMembershipRecord(
                        tenantUuid,
                        "clinic-a",
                        "Clinic A",
                        "PLATFORM_ADMIN",
                        "ACTIVE",
                        new TenantModulesRecord(true, true, true, true, true, true, true, true, true, true)
                )));
        when(tenantModuleService.findForTenant(tenantUuid)).thenReturn(Map.of("APPOINTMENTS", true));
        when(permissionChecker.currentPermissions()).thenReturn(Set.of("platform.read"));

        mockMvc.perform(get("/api/me")
                        .header("X-Tenant-Id", "clinic-a")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("sub-123")
                                .claim("email", "platform.admin@clinic.local")
                                .claim("preferred_username", "platform.admin")
                                .claim("realm_access", Map.of("roles", List.of("platform-admin"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("platform.admin@clinic.local"))
                .andExpect(jsonPath("$.platformAdmin").value(true))
                .andExpect(jsonPath("$.tenantId").value(tenantUuid.toString()))
                .andExpect(jsonPath("$.enabledModules.APPOINTMENTS").value(true))
                .andExpect(jsonPath("$.memberships[0].tenantCode").value("clinic-a"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            RequestContextConfig.class,
            ProviderAuthConfig.class,
            PatientPortalAuthConfig.class,
            MeController.class
    })
    static class TestApplication {
    }
}
