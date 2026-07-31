package com.deepthoughtnet.clinic.api.discover.reference;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.config.SecurityConfig;
import com.deepthoughtnet.clinic.api.config.RequestContextConfig;
import com.deepthoughtnet.clinic.api.discover.ProviderLandingPageController;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthConfig;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderWorkspaceController;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalAuthConfig;
import com.deepthoughtnet.clinic.api.platform.discover.ProviderApplicationReviewApiService;
import com.deepthoughtnet.clinic.api.platform.discover.ProviderApplicationReviewController;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogController;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageService;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = DiscoverReferenceSecurityTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/auth/realms/jeevanam",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureMockMvc
class DiscoverReferenceSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscoverReferenceDataService discoverReferenceDataService;

    @MockBean
    private PublicCatalogFacade publicCatalogFacade;

    @MockBean
    private ProviderApplicationReviewApiService providerApplicationReviewApiService;

    @MockBean
    private DiscoverVerificationService discoverVerificationService;

    @MockBean
    private ProviderOnboardingService providerOnboardingService;

    @MockBean
    private LandingPageService landingPageService;

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
    private PermissionChecker permissionChecker;

    @Test
    void anonymousReferenceCatalogGetsArePublic() throws Exception {
        when(discoverReferenceDataService.listSpecialities()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listServices()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listFacilities()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listOwnerships()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listOrganisationTypes()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listLanguages()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listCountries()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listStates()).thenReturn(sampleOptions());
        when(discoverReferenceDataService.listMedicalCouncils()).thenReturn(sampleOptions());

        mockMvc.perform(get("/api/discover/reference/specialities"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/services"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/facilities"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/ownerships"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/organisation-types"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/languages"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/countries"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/states"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discover/reference/medical-councils"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousWriteRequestsUnderReferencePathRemainProtected() throws Exception {
        mockMvc.perform(post("/api/discover/reference/specialities").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdminManagementEndpointsRemainAuthenticated() throws Exception {
        mockMvc.perform(get("/api/platform/discover/provider-applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingProtectedProviderAndPublicCatalogRoutesRetainSecurityBehavior() throws Exception {
        when(publicCatalogFacade.search(null, null, null, null, null, null, null, 0, 6))
                .thenReturn(new com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse(
                        null,
                        null,
                        null,
                        List.of()
                ));

        mockMvc.perform(get("/api/public/search"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/provider/me"))
                .andExpect(status().isUnauthorized());
    }

    private static List<DiscoverReferenceOptionRecord> sampleOptions() {
        return List.of(new DiscoverReferenceOptionRecord(
                UUID.randomUUID(),
                DiscoverReferenceCategory.SPECIALITY,
                "GENERAL_MEDICINE",
                "General Medicine",
                List.of(),
                1,
                true
        ));
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
            DiscoverReferenceController.class,
            PublicCatalogController.class,
            ProviderApplicationReviewController.class,
            ProviderWorkspaceController.class,
            ProviderLandingPageController.class
    })
    static class TestApplication {
    }
}
